import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../services/resume-state.service';
import { PdfExportService } from '../services/pdf-export.service';
import { ClassicLayoutComponent } from './layouts/classic-layout.component';
import { Modern1ColLayoutComponent } from './layouts/modern-1col-layout.component';
import { Modern2ColLayoutComponent } from './layouts/modern-2col-layout.component';
import { MinimalLayoutComponent } from './layouts/minimal-layout.component';
import { ExecutiveLayoutComponent } from './layouts/executive-layout.component';
import { CreativeLayoutComponent } from './layouts/creative-layout.component';

@Component({
  selector: 'app-resume-preview',
  standalone: true,
  imports: [
    CommonModule,
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

  async downloadPdf(): Promise<void> {
    if (!this.previewRef) return;
    // Display variant: an anonymised export must not carry the real name in its filename
    const name = this.state.displayPersonalInfo().fullName || 'resume';
    await this.pdfExport.download(this.previewRef.nativeElement, name, this.state.settings().documentSize);
  }

  async printResume(): Promise<void> {
    if (!this.previewRef) return;
    await this.pdfExport.print(this.previewRef.nativeElement);
  }
}
