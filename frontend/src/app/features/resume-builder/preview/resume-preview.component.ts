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

  get docWidth(): number {
    return this.state.settings().documentSize === 'A4' ? 794 : 816;
  }

  get docHeight(): number {
    return this.state.settings().documentSize === 'A4' ? 1123 : 1056;
  }

  async downloadPdf(): Promise<void> {
    if (!this.previewRef) return;
    const name = this.state.personalInfo().fullName || 'resume';
    await this.pdfExport.download(this.previewRef.nativeElement, name, this.state.settings().documentSize);
  }

  async printResume(): Promise<void> {
    if (!this.previewRef) return;
    await this.pdfExport.print(this.previewRef.nativeElement);
  }
}
