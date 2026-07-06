import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiApiService } from '../../core/api/ai.api';
import { PdfTemplatesApiService } from '../../core/api/pdf-templates.api';
import { StructuredDocumentTemplatesApiService } from '../../core/api/structured-document-templates.api';
import { StructuredDocument, DocumentTemplateOption, STRUCTURED_DOCUMENT_TEMPLATES } from '../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { AtsReportPanelComponent } from './components/ats-report-panel.component';
import { runAction } from '../../shared/utils/async-ui';
import { downloadBlob } from '../../shared/utils/file-download';

const SECTION_ORDER_KEY = 'cv_section_order';

@Component({
  selector: 'app-cv-page',
  standalone: true,
  imports: [CommonModule, FormsModule, StructuredDocumentRendererComponent, AtsReportPanelComponent],
  styles: [`
    @media print {
      .no-print { display: none !important; }
      body * { visibility: hidden; }
      .print-area, .print-area * { visibility: visible; }
      .print-area { position: fixed; top: 0; left: 0; width: 210mm; }
    }
  `],
  template: `
    <div class="max-w-6xl mx-auto space-y-6">

      <!-- Header + actions -->
      <div class="no-print flex items-center justify-between">
        <h1 class="text-2xl font-bold text-gray-900">My CV</h1>
        <div class="flex gap-2">
          <button (click)="downloadPdf()" [disabled]="downloadingPdf" class="btn-secondary text-sm">
            {{ downloadingPdf ? 'Generating PDF...' : 'Download PDF' }}
          </button>
        </div>
      </div>

      @if (loading) {
        <div class="text-center text-gray-500 py-12">Loading CV...</div>
      } @else if (error) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ error }}</div>
      } @else if (document) {
        <div class="grid grid-cols-1 lg:grid-cols-4 gap-6">

          <!-- Left sidebar: template picker + section order -->
          <div class="no-print lg:col-span-1 space-y-4">

            <!-- Template picker -->
            <div class="bg-white border border-gray-200 rounded-lg p-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">Template</h3>
              <div class="space-y-2">
                @for (tpl of cvTemplates; track tpl.id) {
                  <button (click)="selectTemplate(tpl)"
                          [ngClass]="selectedTemplateId === tpl.id
                            ? 'w-full text-left px-3 py-2 rounded text-sm font-medium bg-blue-600 text-white'
                            : 'w-full text-left px-3 py-2 rounded text-sm text-gray-700 hover:bg-gray-50 border border-gray-200'">
                    {{ tpl.label }}
                    <span class="ml-1 text-xs opacity-70">{{ tpl.layoutType === 'two-column' ? '2-col' : '1-col' }}</span>
                  </button>
                }
              </div>
            </div>

            <div class="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
              <h3 class="text-sm font-semibold text-gray-700">Appearance</h3>
              <label class="flex items-center gap-2 text-sm text-gray-700">
                <input type="checkbox" [(ngModel)]="showProfileImage" class="rounded border-gray-300" />
                Show profile image
              </label>
              <div>
                <label class="label">Primary Color</label>
                <input type="color" [(ngModel)]="primaryColor" class="h-10 w-full rounded-md border border-gray-300 bg-white px-2" />
              </div>
              <div>
                <label class="label">Accent Color</label>
                <input type="color" [(ngModel)]="accentColor" class="h-10 w-full rounded-md border border-gray-300 bg-white px-2" />
              </div>
              <div>
                <label class="label">Font</label>
                <select [(ngModel)]="fontFamily" class="input">
                  <option value="Arial">Arial</option>
                  <option value="Inter">Inter</option>
                  <option value="Calibri">Calibri</option>
                  <option value="Georgia">Georgia</option>
                  <option value="Times New Roman">Times New Roman</option>
                </select>
              </div>
              <div>
                <label class="label">Font Size</label>
                <select [(ngModel)]="fontScale" class="input">
                  <option value="small">Small</option>
                  <option value="normal">Normal</option>
                  <option value="large">Large</option>
                </select>
              </div>
            </div>

            <!-- Section order -->
            <div class="bg-white border border-gray-200 rounded-lg p-4">
              <h3 class="text-sm font-semibold text-gray-700 mb-3">Section Order</h3>
              <div class="space-y-1.5">
                @for (sec of orderedSections; track sec.id; let i = $index) {
                  <div class="flex items-center gap-2 text-sm text-gray-700">
                    <span class="flex-1 truncate capitalize">{{ sec.heading }}</span>
                    <div class="flex gap-1">
                      <button [disabled]="i === 0"
                              (click)="moveSection(i, -1)"
                              class="w-6 h-6 flex items-center justify-center rounded hover:bg-gray-100 disabled:opacity-30 text-xs">
                        ↑
                      </button>
                      <button [disabled]="i === orderedSections.length - 1"
                              (click)="moveSection(i, 1)"
                              class="w-6 h-6 flex items-center justify-center rounded hover:bg-gray-100 disabled:opacity-30 text-xs">
                        ↓
                      </button>
                    </div>
                  </div>
                }
              </div>
            </div>

            <!-- ATS report -->
            <app-ats-report-panel [report]="document.atsReport"></app-ats-report-panel>
          </div>

          <!-- Main: document preview -->
          <div class="lg:col-span-3 print-area">
            <app-structured-document-renderer [document]="renderedDocument"></app-structured-document-renderer>
          </div>
        </div>
      }
    </div>
  `
})
export class CvPageComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private pdfApi = inject(PdfTemplatesApiService);
  private structuredTemplateApi = inject(StructuredDocumentTemplatesApiService);

  document: StructuredDocument | null = null;
  loading = false;
  downloadingPdf = false;
  error = '';
  selectedTemplateId = 'cv-modern-professional';
  showProfileImage = false;
  primaryColor = '#18324a';
  accentColor = '#cbd8e3';
  fontFamily = 'Inter';
  fontScale = 'normal';

  cvTemplates: DocumentTemplateOption[] = STRUCTURED_DOCUMENT_TEMPLATES.filter(t =>
    t.documentTypes.includes('CV')
  );

  orderedSections: StructuredDocument['sections'] = [];

  get renderedDocument(): StructuredDocument {
    if (!this.document) return this.document!;
    return {
      ...this.document,
      templateId: this.selectedTemplateId,
      sections: this.orderedSections,
      options: {
        ...(this.document.options ?? { showProfileImage: false }),
        showProfileImage: this.showProfileImage,
        theme: {
          primaryColor: this.primaryColor,
          accentColor: this.accentColor,
          fontFamily: this.fontFamily,
          fontScale: this.fontScale
        }
      }
    };
  }

  ngOnInit(): void {
    this.structuredTemplateApi.getActive().subscribe({
      next: templates => {
        const cvTemplates = templates.filter(t => t.documentTypes.includes('CV'));
        if (cvTemplates.length > 0) {
          this.cvTemplates = cvTemplates;
          this.applyTemplateDefaults(this.cvTemplates.find(t => t.id === this.selectedTemplateId) ?? this.cvTemplates[0]);
        }
      }
    });
    runAction({
      action$: this.aiApi.getCvRenderModel(this.selectedTemplateId),
      setLoading: value => this.loading = value,
      setError: message => this.error = message,
      errorMessage: 'Could not load CV. Make sure your profile is complete.',
      next: doc => {
        this.document = doc;
        this.selectedTemplateId = doc.templateId || this.selectedTemplateId;
        this.orderedSections = this.loadSectionOrder(doc.sections ?? []);
        this.showProfileImage = doc.options?.showProfileImage ?? false;
        this.applyTemplateDefaults(this.cvTemplates.find(t => t.id === this.selectedTemplateId));
      }
    });
  }

  selectTemplate(tpl: DocumentTemplateOption): void {
    this.selectedTemplateId = tpl.id;
    this.applyTemplateDefaults(tpl);
    if (this.document) {
      this.document = { ...this.document, templateId: tpl.id };
    }
  }

  moveSection(index: number, direction: -1 | 1): void {
    const sections = [...this.orderedSections];
    const target = index + direction;
    if (target < 0 || target >= sections.length) return;
    [sections[index], sections[target]] = [sections[target], sections[index]];
    this.orderedSections = sections;
    this.saveSectionOrder(sections);
  }

  downloadPdf(): void {
    if (!this.document) return;
    runAction({
      action$: this.pdfApi.exportStructuredPdf(this.renderedDocument),
      setLoading: value => this.downloadingPdf = value,
      setError: message => this.error = message,
      errorMessage: 'Could not generate the PDF. Please try again.',
      next: blob => {
        downloadBlob(blob, 'cv.pdf');
      }
    });
  }

  private applyTemplateDefaults(template?: DocumentTemplateOption): void {
    if (!template) return;
    this.showProfileImage = template.supportsProfileImage ? this.showProfileImage : false;
    this.primaryColor = template.defaultTheme?.primaryColor ?? this.primaryColor;
    this.accentColor = template.defaultTheme?.accentColor ?? this.accentColor;
    this.fontFamily = template.defaultTheme?.fontFamily ?? this.fontFamily;
    this.fontScale = template.defaultTheme?.fontScale ?? this.fontScale;
  }

  private loadSectionOrder(sections: StructuredDocument['sections']): StructuredDocument['sections'] {
    try {
      const saved = localStorage.getItem(SECTION_ORDER_KEY);
      if (!saved) return sections;
      const order: string[] = JSON.parse(saved);
      const byId = new Map(sections.map(s => [s.id, s]));
      const ordered = order.map(id => byId.get(id)).filter(Boolean) as StructuredDocument['sections'];
      const remaining = sections.filter(s => !order.includes(s.id));
      return [...ordered, ...remaining];
    } catch {
      return sections;
    }
  }

  private saveSectionOrder(sections: StructuredDocument['sections']): void {
    localStorage.setItem(SECTION_ORDER_KEY, JSON.stringify(sections.map(s => s.id)));
  }
}
