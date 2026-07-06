import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AiApiService } from '../../../core/api/ai.api';
import { PdfTemplatesApiService } from '../../../core/api/pdf-templates.api';
import { StructuredDocumentTemplatesApiService } from '../../../core/api/structured-document-templates.api';
import { StructuredDocument, DocumentTemplateOption, STRUCTURED_DOCUMENT_TEMPLATES } from '../../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { AtsReportPanelComponent } from './components/ats-report-panel.component';
import { runAction } from '../../../shared/utils/async-ui';
import { downloadBlob } from '../../../shared/utils/file-download';

const SECTION_ORDER_KEY = 'cv_section_order';

@Component({
  selector: 'app-cv-page',
  standalone: true,
  imports: [CommonModule, FormsModule, StructuredDocumentRendererComponent, AtsReportPanelComponent],
  styleUrls: ['./cv-page.component.css'],
  templateUrl: './cv-page.component.html'
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
