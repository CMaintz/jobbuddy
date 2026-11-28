import { Component, DestroyRef, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, debounceTime, merge, of, scan, switchMap } from 'rxjs';
import { ResumeStateService } from '../services/resume-state.service';
import { toResumeDesign } from '../services/resume-design';
import { AtsPdfService, resumeDataToAts } from '../../../shared/services/ats-pdf.service';
import { translatedAtsHeadings } from '../../../shared/services/ats-headings';
import { DocumentRenderApiService, RendererBusyError, renderFailureKey, savePdf } from '../../../core/api/document-render.api';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { PageBudgetWarningComponent } from '../../../shared/components/page-budget-warning/page-budget-warning.component';
import { ExportedPageBudget } from '../../../shared/page-budget';
import { NotifyService } from '../../../shared/services/notify.service';

/** How long typing pauses before the preview is re-rendered: one render per pause, not per key. */
const PREVIEW_DEBOUNCE_MS = 350;

/**
 * The builder's preview is the SVG pages of the same Typst compile the PDF export makes, so the
 * downloaded PDF looks exactly like what the user saw, page breaks included.
 */
@Component({
  selector: 'app-resume-preview',
  imports: [TranslateModule, JbButtonComponent, PageBudgetWarningComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './resume-preview.component.html',
})
export class ResumePreviewComponent {
  protected state = inject(ResumeStateService);
  private atsPdf = inject(AtsPdfService);
  private renderApi = inject(DocumentRenderApiService);
  private translate = inject(TranslateService);
  private notify = inject(NotifyService);

  /**
   * Ticks on every language switch or translation load. translate.instant creates no signal
   * dependency, so without this the headings stay in the language the design was first built in.
   */
  private readonly translations = toSignal(
    merge(this.translate.onLangChange, this.translate.onTranslationChange, this.translate.onDefaultLangChange)
      .pipe(scan(count => count + 1, 0)),
    { initialValue: 0 });

  /** Page count of the last PDF the user downloaded, against the two-page CV budget. */
  readonly exportedPages = new ExportedPageBudget('cv', computed(() => [
    this.state.resumeData(), this.state.settings(), this.state.displayPersonalInfo(),
  ]));

  /** The design both the preview and the export render, with headings in the current language. */
  readonly design = computed(() => {
    this.translations();
    return toResumeDesign({
      personalInfo: this.state.displayPersonalInfo(),
      socials: this.state.displaySocials(),
      data: this.state.resumeData(),
      settings: this.state.settings(),
      skillGroups: this.state.skillGroups(),
    }, key => this.translate.instant(key));
  });

  readonly downloading = signal(false);

  /** Object URLs of the current preview pages; null until the first render arrives. */
  readonly pages = signal<string[] | null>(null);
  readonly previewFailed = signal(false);
  /** The last preview was refused because every renderer was taken, not because it failed. */
  readonly previewBusy = signal(false);

  get docWidth(): number {
    return this.state.settings().documentSize === 'A4' ? 794 : 816;
  }

  constructor() {
    toObservable(this.design).pipe(
      debounceTime(PREVIEW_DEBOUNCE_MS),
      switchMap(design => this.renderApi.previewResume(design).pipe(
        catchError((err: unknown) => {
          this.previewBusy.set(err instanceof RendererBusyError);
          return of(null);
        }))),
      takeUntilDestroyed(),
    ).subscribe(svgs => {
      this.previewFailed.set(svgs === null);
      if (svgs) this.showPages(svgs);
    });
    inject(DestroyRef).onDestroy(() => this.revoke(this.pages()));
  }

  /** The designed CV as a real text PDF, laid out exactly as the preview shows it. */
  async downloadPdf(): Promise<void> {
    // A render takes a moment; a second click would start a second one.
    if (this.downloading()) return;
    this.downloading.set(true);
    // Display variant: an anonymised export must not carry the real name in its filename
    const name = this.state.displayPersonalInfo().fullName || 'resume';
    try {
      await this.exportedPages.track(this.renderApi.exportResumePdf(this.design()).then(pdf => savePdf(pdf, name)));
    } catch (err) {
      this.notify.error(renderFailureKey(err));
    } finally {
      this.downloading.set(false);
    }
  }

  /** Text-based single-column PDF: selectable text, safe for ATS parsers. */
  async downloadAtsPdf(): Promise<void> {
    const pi = this.state.displayPersonalInfo();
    const model = resumeDataToAts(
      { ...this.state.resumeData(), socials: this.state.displaySocials() }, pi, this.state.settings(),
      translatedAtsHeadings(key => this.translate.instant(key)));
    await this.exportedPages.track(this.atsPdf.downloadResume(model, `${pi.fullName || 'resume'} - ATS`));
  }

  /** Prints the preview pages, which are vector SVG, so the print is as sharp as the PDF. */
  printResume(): void {
    const pages = this.pages();
    if (!pages?.length) return;
    const iframe = document.createElement('iframe');
    iframe.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0';
    document.body.appendChild(iframe);
    const doc = iframe.contentDocument!;
    doc.open();
    doc.write('<style>@page{margin:0}body{margin:0}img{display:block;width:100%;break-after:page}</style>');
    for (const url of pages) doc.write(`<img src="${url}">`);
    doc.close();
    iframe.onload = () => {
      iframe.contentWindow!.focus();
      iframe.contentWindow!.print();
      setTimeout(() => document.body.removeChild(iframe), 1000);
    };
  }

  private showPages(svgs: string[]): void {
    const previous = this.pages();
    this.pages.set(svgs.map(svg => URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }))));
    this.revoke(previous);
  }

  private revoke(urls: string[] | null): void {
    urls?.forEach(url => URL.revokeObjectURL(url));
  }
}
