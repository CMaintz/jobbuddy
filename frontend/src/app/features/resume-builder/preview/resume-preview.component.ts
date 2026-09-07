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
  template: `
    <div class="flex flex-col items-center gap-4">
      <!-- PDF controls -->
      <div class="flex gap-2 w-full max-w-[794px]">
        <button
          class="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          (click)="downloadPdf()"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/>
          </svg>
          Download PDF
        </button>
        <button
          class="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-white text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
          (click)="printResume()"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z"/>
          </svg>
          Print
        </button>
      </div>

      <!-- Preview container -->
      <div
        #previewContainer
        id="previewContainer"
        class="bg-white shadow-2xl overflow-hidden"
        [style.width.px]="docWidth"
        [style.min-height.px]="docHeight"
        [style.font-family]="state.settings().fontFamily"
      >
        @switch (state.settings().template) {
          @case ('classic') {
            @defer (on idle) {
              <app-classic-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @case ('modern') {
            @defer (on idle) {
              <app-modern-1col-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @case ('modern-2col') {
            @defer (on idle) {
              <app-modern-2col-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @case ('minimal') {
            @defer (on idle) {
              <app-minimal-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @case ('executive') {
            @defer (on idle) {
              <app-executive-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @case ('creative') {
            @defer (on idle) {
              <app-creative-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
          @default {
            @defer (on idle) {
              <app-classic-layout />
            } @placeholder {
              <div class="animate-pulse bg-gray-50" [style.height.px]="docHeight"></div>
            }
          }
        }
      </div>
    </div>
  `,
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
