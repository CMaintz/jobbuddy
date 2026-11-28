import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AtsPdfService } from '../../../shared/services/ats-pdf.service';
import { DocumentRenderApiService, RendererBusyError } from '../../../core/api/document-render.api';
import { NotifyService } from '../../../shared/services/notify.service';
import { ResumeDesign } from '../../../core/models/resume-design.model';
import { ResumeDraftApiService } from '../services/resume-draft-api.service';
import { ResumeStateService } from '../services/resume-state.service';
import { ResumePreviewComponent } from './resume-preview.component';
import type { Mocked } from 'vitest';
import { spyObj } from '../../../../testing/spy-obj';

/** Longer than the preview's debounce, so the render it waits for has been requested. */
const PREVIEW_SETTLE_MS = 500;

describe('ResumePreviewComponent page budget', () => {
  let fixture: ComponentFixture<ResumePreviewComponent>;
  let state: ResumeStateService;
  let render: Mocked<DocumentRenderApiService>;

  beforeEach(() => {
    render = spyObj<DocumentRenderApiService>(['previewResume', 'exportResumePdf']);
    render.previewResume.mockReturnValue(of(['<svg xmlns="http://www.w3.org/2000/svg"/>']));
    render.exportResumePdf.mockReturnValue(Promise.resolve({ blob: new Blob(), pageCount: 3 }));
    TestBed.configureTestingModule({
      imports: [ResumePreviewComponent, TranslateModule.forRoot()],
      providers: [
        { provide: DocumentRenderApiService, useValue: render },
        { provide: AtsPdfService, useValue: { downloadResume: () => Promise.resolve(3) } },
        { provide: ResumeDraftApiService, useValue: {} },
      ],
    });
    state = TestBed.inject(ResumeStateService);
    fixture = TestBed.createComponent(ResumePreviewComponent);
    fixture.detectChanges();
  });

  function warning(): string {
    const el = (fixture.nativeElement as HTMLElement).querySelector('jb-page-budget-warning');
    return el?.textContent?.trim() ?? '';
  }

  async function exportCv(): Promise<void> {
    await fixture.componentInstance.downloadAtsPdf();
    fixture.detectChanges();
  }

  it('hides the warning once the CV text changes, since it described the old file', async () => {
    await exportCv();

    state.updatePersonalInfo({ summary: 'Kortere resumé' });
    fixture.detectChanges();

    expect(warning()).toBe('');
  });

  it('hides the warning when a layout setting changes the page count', async () => {
    await exportCv();

    state.updateSettings({ fontSize: 'sm' });
    fixture.detectChanges();

    expect(warning()).toBe('');
  });

  it('warns again after the edited CV is downloaded and still runs over', async () => {
    await exportCv();
    state.updatePersonalInfo({ summary: 'Kortere resumé' });
    fixture.detectChanges();

    await exportCv();

    expect(warning()).toContain('pageBudget.cv.over');
  });

  it('previews the pages the backend rendered for the current design', async () => {
    // Bug: the preview goes back to an HTML layout, so the PDF no longer looks like what she saw.
    state.updatePersonalInfo({ fullName: 'Mette Højgaard' });
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, PREVIEW_SETTLE_MS));
    fixture.detectChanges();

    const imgs = (fixture.nativeElement as HTMLElement).querySelectorAll('#previewContainer img');
    expect(imgs.length).toBe(1);
    const previewed: ResumeDesign = render.previewResume.mock.lastCall![0];
    expect(previewed.person.name).toBe('Mette Højgaard');
  });

  it('sends the anonymised design to the PDF export', async () => {
    // Bug: the export reads the raw personal info, so an anonymised CV carries her real name,
    // e-mail and links into the PDF.
    state.updatePersonalInfo({ fullName: 'Mette Højgaard', email: 'mette@example.dk' });
    state.updateSettings({ anonymise: true });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    await fixture.componentInstance.downloadPdf();

    const exported: ResumeDesign = render.exportResumePdf.mock.lastCall![0];
    expect(exported.person.name).not.toContain('Mette');
    expect(exported.person.email).toBe('');
    expect(exported.socials).toEqual([]);
  });

  it('warns when the designed PDF the backend measured runs over two pages', async () => {
    // Bug: the page count header is not read, so an overlong designed CV goes out unwarned.
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    await fixture.componentInstance.downloadPdf();
    fixture.detectChanges();

    expect(warning()).toContain('pageBudget.cv.over');
  });

  it('exports headings in the language she switched to, not the one the design was first built in', async () => {
    // Bug: the design only recomputes when CV data changes, so after switching DA to EN the PDF
    // (and the preview) keep the Danish headings until she edits something.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('da', { resumeBuilder: { cv: { experience: 'Erfaring' } } });
    translate.setTranslation('en', { resumeBuilder: { cv: { experience: 'Experience' } } });
    translate.use('da');
    fixture.componentInstance.design();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    translate.use('en');
    await fixture.componentInstance.downloadPdf();

    const exported: ResumeDesign = render.exportResumePdf.mock.lastCall![0];
    expect(exported.labels['experience']).toBe('Experience');
  });

  it('says the renderer is busy, not that the export failed, when every slot is taken', async () => {
    // Bug: a busy renderer reads "something went wrong", so she thinks the CV is broken.
    render.exportResumePdf.mockReturnValue(Promise.reject(new RendererBusyError()));
    const error = vi.spyOn(TestBed.inject(NotifyService), 'error');

    await fixture.componentInstance.downloadPdf();

    expect(error).toHaveBeenCalledWith('notify.rendererBusy');
  });

  it('says the preview is waiting for the renderer when every slot is taken', async () => {
    render.previewResume.mockReturnValue(throwError(() => new RendererBusyError()));
    state.updatePersonalInfo({ fullName: 'Mette Højgaard' });
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, PREVIEW_SETTLE_MS));
    fixture.detectChanges();

    const status = (fixture.nativeElement as HTMLElement).querySelector('[role="status"]');
    expect(status?.textContent).toContain('resumeBuilder.preview.busy');
  });
});
