import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ResumeStateService } from '../services/resume-state.service';
import { PdfExportService } from '../services/pdf-export.service';
import { AtsPdfService, resumeDataToAts } from '../../../shared/services/ats-pdf.service';
import { ClassicLayoutComponent } from './layouts/classic-layout.component';
import { Modern1ColLayoutComponent } from './layouts/modern-1col-layout.component';
import { Modern2ColLayoutComponent } from './layouts/modern-2col-layout.component';
import { MinimalLayoutComponent } from './layouts/minimal-layout.component';
import { ExecutiveLayoutComponent } from './layouts/executive-layout.component';
import { CreativeLayoutComponent } from './layouts/creative-layout.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';

@Component({
  selector: 'app-resume-preview',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    JbButtonComponent,
    ClassicLayoutComponent,
    Modern1ColLayoutComponent,
    Modern2ColLayoutComponent,
    MinimalLayoutComponent,
    ExecutiveLayoutComponent,
    CreativeLayoutComponent,
  ],
  templateUrl: './resume-preview.component.html',
})
export class ResumePreviewComponent {
  @ViewChild('previewContainer') previewRef!: ElementRef<HTMLElement>;

  protected state = inject(ResumeStateService);
  private pdfExport = inject(PdfExportService);
  private atsPdf = inject(AtsPdfService);
  private translate = inject(TranslateService);

  private static readonly FONT_SCALES: Record<string, number> = { sm: 0.85, md: 1, lg: 1.15, xl: 1.3 };

  get docWidth(): number {
    return this.state.settings().documentSize === 'A4' ? 794 : 816;
  }

  get docHeight(): number {
    return this.state.settings().documentSize === 'A4' ? 1123 : 1056;
  }

  /** Typography settings → CSS vars consumed by the #previewContainer rules in styles.css */
  get typographyVars(): string {
    const s = this.state.settings();
    const scale = ResumePreviewComponent.FONT_SCALES[s.fontSize] ?? 1;
    return `--rb-font-scale:${scale};` +
      `--rb-line:${s.lineSpacing ?? 1.5};` +
      `--rb-text:${s.textColor ?? '#1f2937'};` +
      `--rb-section-gap:${s.sectionSpacing ?? 20}px;`;
  }

  /** Pixel-perfect capture of the styled preview (image-based — not ATS-parseable). */
  async downloadPdf(): Promise<void> {
    if (!this.previewRef) return;
    // Display variant: an anonymised export must not carry the real name in its filename
    const name = this.state.displayPersonalInfo().fullName || 'resume';
    await this.pdfExport.download(this.previewRef.nativeElement, name, this.state.settings().documentSize);
  }

  /** Text-based single-column PDF — selectable text, safe for ATS parsers. */
  async downloadAtsPdf(): Promise<void> {
    const pi = this.state.displayPersonalInfo();
    // Headings follow the interface language. These keys are the frontend half of the CV heading
    // vocabulary — the backend half is CvSectionLabels, and the two have to agree or the same CV
    // reads differently depending on which path exported it.
    const model = resumeDataToAts(
      { ...this.state.resumeData(), socials: this.state.displaySocials() }, pi, this.state.settings(),
      {
        strengths: this.translate.instant('resumeBuilder.section.strengths'),
        experience: this.translate.instant('resumeBuilder.section.experience'),
        skills: this.translate.instant('resumeBuilder.section.skills'),
        projects: this.translate.instant('resumeBuilder.section.projects'),
        education: this.translate.instant('resumeBuilder.section.education'),
        certifications: this.translate.instant('resumeBuilder.section.certifications'),
        languages: this.translate.instant('resumeBuilder.section.languages'),
      });
    await this.atsPdf.downloadResume(model, `${pi.fullName || 'resume'} - ATS`);
  }

  async printResume(): Promise<void> {
    if (!this.previewRef) return;
    await this.pdfExport.print(this.previewRef.nativeElement);
  }
}
