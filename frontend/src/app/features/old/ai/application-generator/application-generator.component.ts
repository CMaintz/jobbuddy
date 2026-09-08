import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AiApiService } from '../../../../core/api/ai.api';
import { JobsApiService } from '../../../../core/api/jobs.api';
import { PdfTemplatesApiService } from '../../../../core/api/pdf-templates.api';
import { StructuredDocumentTemplatesApiService } from '../../../../core/api/structured-document-templates.api';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { DocumentTemplateOption, StructuredDocument, STRUCTURED_DOCUMENT_TEMPLATES } from '../../../../core/models/structured-document.model';
import { Application, ApplicationStatus } from '../../../../core/models/application.model';
import { downloadBlob } from '../../../../shared/utils/file-download';
import { GenerationConfigFormComponent } from './generation-config-form.component';
import { GeneratedDocumentEditorComponent } from './generated-document-editor.component';
import { DocumentRefinePanelComponent } from './document-refine-panel.component';
import { ActiveGeneratedDocument, APPLICATION_GENERATOR_LANGUAGES, ChatMessage } from './application-generator.types';
import { applyRenderOptions, cvPlainText, editorHtml } from './structured-document-content';
import * as generatorActions from './application-generator.actions';

@Component({
  selector: 'app-application-generator',
  standalone: true,
  imports: [CommonModule, GenerationConfigFormComponent, GeneratedDocumentEditorComponent, DocumentRefinePanelComponent],
  templateUrl: './application-generator.component.html'
})
export class ApplicationGeneratorComponent implements OnInit {

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private aiApi = inject(AiApiService);
  private jobsApi = inject(JobsApiService);
  private pdfApi = inject(PdfTemplatesApiService);
  private structuredTemplateApi = inject(StructuredDocumentTemplatesApiService);
  private applicationsApi = inject(ApplicationsApiService);

  languages = APPLICATION_GENERATOR_LANGUAGES;
  structuredTemplates: DocumentTemplateOption[] = STRUCTURED_DOCUMENT_TEMPLATES;

  loading = false;
  refining = false;
  downloadingPdf = false;
  savingApplication = false;
  error = '';
  applicationSaveMessage = '';
  modelLabel = '';
  structuredDocument: StructuredDocument | null = null;
  applicationDocument: StructuredDocument | null = null;
  pairedCvDocument: StructuredDocument | null = null;
  activeDocument: ActiveGeneratedDocument = 'single';

  editorHtml = '';
  currentContent = '';
  showEditor = false;
  copied = false;

  chatHistory: ChatMessage[] = [];
  chatMessage = '';

  jobTitle = '';
  jobDescription = '';
  savedApplication: Application | null = null;

  form = this.fb.group({
    documentType: ['COVER_LETTER', Validators.required],
    jobId: [''],
    jobDescription: [''],
    structuredTemplateId: ['application-modern'],
    showProfileImage: [false],
    primaryColor: ['#18324a'],
    accentColor: ['#cbd8e3'],
    fontFamily: ['Arial'],
    fontScale: ['normal'],
    customInstructions: [''],
    motivationText: [''],
    targetLanguage: ['Danish']
  });

  get availableStructuredTemplates() {
    const type = this.form.value.documentType as any;
    return this.structuredTemplates.filter(template => template.documentTypes.includes(type));
  }

  ngOnInit(): void {
    this.form.controls.documentType.valueChanges.subscribe(() => {
      const first = this.availableStructuredTemplates[0];
      if (first) {
        this.form.patchValue({ structuredTemplateId: first.id }, { emitEvent: false });
        this.applyTemplateDefaults(first.id);
      }
      this.structuredDocument = null;
      this.applicationDocument = null;
      this.pairedCvDocument = null;
      this.activeDocument = 'single';
    });
    this.form.controls.structuredTemplateId.valueChanges.subscribe(templateId => {
      this.applyTemplateDefaults(templateId);
    });
    ['showProfileImage', 'primaryColor', 'accentColor', 'fontFamily', 'fontScale'].forEach(controlName => {
      this.form.get(controlName)?.valueChanges.subscribe(() => {
        if (this.structuredDocument) {
          this.structuredDocument = this.withRenderOptions(this.structuredDocument);
          this.persistActiveDocumentEdits();
        }
      });
    });

    const jobId = this.route.snapshot.queryParamMap.get('jobId');
    if (jobId) {
      this.form.patchValue({ jobId });
      this.jobsApi.getById(jobId).subscribe(j => {
        this.jobTitle = j.title + ' @ ' + j.companyName;
        this.jobDescription = j.descriptionClean ?? '';
        this.form.patchValue({ jobDescription: this.jobDescription });
      });
    }
    this.structuredTemplateApi.getActive().subscribe({
      next: templates => {
        if (templates.length === 0) return;
        this.structuredTemplates = templates;
        const selectedTemplateId = this.form.value.structuredTemplateId;
        if (!this.availableStructuredTemplates.some(t => t.id === selectedTemplateId)) {
          const first = this.availableStructuredTemplates[0];
          if (first) {
            this.form.patchValue({ structuredTemplateId: first.id }, { emitEvent: false });
            this.applyTemplateDefaults(first.id);
          }
        } else {
          this.applyTemplateDefaults(selectedTemplateId);
        }
      },
      error: () => {
        this.structuredTemplates = STRUCTURED_DOCUMENT_TEMPLATES;
      }
    });
  }

  generate(): void {
    generatorActions.generate(this, this.aiApi);
  }

  generateApplicationSet(): void {
    generatorActions.generateApplicationSet(this, this.aiApi);
  }

  sendChat(): void {
    generatorActions.sendChat(this, this.aiApi);
  }

  copyContent(): void {
    generatorActions.copyContent(this);
  }

  downloadPdf(): void {
    generatorActions.downloadPdf(this, this.pdfApi);
  }

  downloadCvPdf(): void {
    generatorActions.downloadCvPdf(this, this.pdfApi);
  }

  refreshStructuredApplication(): void {
    if (!this.structuredDocument || this.structuredDocument.documentType === 'CV') return;
    this.structuredDocument = { ...this.structuredDocument, bodyContent: this.currentContent };
    this.persistActiveDocumentEdits();
  }

  saveToApplication(status: ApplicationStatus): void {
    generatorActions.saveToApplication(this, this.aiApi, this.applicationsApi, status);
  }

  moveSection(index: number, direction: -1 | 1): void {
    generatorActions.moveSection(this, index, direction);
  }

  removeSection(index: number): void {
    generatorActions.removeSection(this, index);
  }

  addCustomSection(heading: string, body: string): void {
    generatorActions.addCustomSection(this, heading, body);
  }

  switchActiveDocument(next: 'application' | 'cv'): void {
    if (!this.applicationDocument || !this.pairedCvDocument) return;
    this.persistActiveDocumentEdits();
    this.activeDocument = next;
    this.structuredDocument = next === 'application'
      ? this.applicationDocument
      : this.pairedCvDocument;
    this.loadCurrentDocumentIntoEditor();
  }

  savePdfBlob(blob: Blob, filename = 'document.pdf'): void {
    downloadBlob(blob, filename);
    this.downloadingPdf = false;
  }

  private refreshCurrentStructuredDocument(): StructuredDocument {
    if (!this.structuredDocument) throw new Error('No structured document');
    if (this.structuredDocument.documentType === 'CV') {
      this.currentContent = cvPlainText(this.structuredDocument);
      return this.structuredDocument;
    }
    const document = { ...this.structuredDocument, bodyContent: this.currentContent };
    this.structuredDocument = document;
    return document;
  }

  persistActiveDocumentEdits(): void {
    if (!this.structuredDocument) return;
    const document = this.refreshCurrentStructuredDocument();
    if (this.activeDocument === 'application') {
      this.applicationDocument = document;
      return;
    }
    if (this.activeDocument === 'cv') {
      this.pairedCvDocument = document;
    }
  }

  loadCurrentDocumentIntoEditor(): void {
    if (!this.structuredDocument) return;
    if (this.structuredDocument.documentType === 'CV') {
      this.currentContent = cvPlainText(this.structuredDocument);
      this.editorHtml = '';
      return;
    }
    this.setEditorContent(this.structuredDocument.bodyContent ?? '');
  }

  rememberGeneratedDocument(document: StructuredDocument): void {
    if (document.documentType === 'CV') {
      this.pairedCvDocument = document;
      if (this.activeDocument === 'cv' || !this.applicationDocument) {
        this.structuredDocument = document;
      }
      return;
    }

    if (this.applicationDocument || this.activeDocument === 'application') {
      this.applicationDocument = document;
    }
    if (this.activeDocument !== 'cv') {
      this.structuredDocument = document;
    }
  }

  withRenderOptions(document: StructuredDocument): StructuredDocument {
    return applyRenderOptions(document, this.form.value.showProfileImage ?? false, this.currentTheme());
  }

  currentTheme() {
    return {
      primaryColor: this.form.value.primaryColor ?? '#18324a',
      accentColor: this.form.value.accentColor ?? '#cbd8e3',
      fontFamily: this.form.value.fontFamily ?? 'Arial',
      fontScale: this.form.value.fontScale ?? 'normal'
    };
  }

  private applyTemplateDefaults(templateId?: string | null): void {
    const template = this.structuredTemplates.find(t => t.id === templateId);
    if (!template) return;
    const theme = template.defaultTheme;
    this.form.patchValue({
      showProfileImage: template.supportsProfileImage ? this.form.value.showProfileImage ?? false : false,
      primaryColor: theme?.primaryColor ?? this.form.value.primaryColor ?? '#18324a',
      accentColor: theme?.accentColor ?? this.form.value.accentColor ?? '#cbd8e3',
      fontFamily: theme?.fontFamily ?? this.form.value.fontFamily ?? 'Arial',
      fontScale: theme?.fontScale ?? this.form.value.fontScale ?? 'normal'
    }, { emitEvent: false });
    if (this.structuredDocument) {
      this.structuredDocument = this.withRenderOptions(this.structuredDocument);
    }
  }

  pairedCvTemplateId(applicationTemplate?: DocumentTemplateOption): string {
    const sameFamily = applicationTemplate?.familyId
      ? this.structuredTemplates.find(t =>
          t.familyId === applicationTemplate.familyId && t.documentTypes.includes('CV'))
      : null;
    return sameFamily?.id
      ?? this.structuredTemplates.find(t => t.id === 'cv-modern-professional')?.id
      ?? this.structuredTemplates.find(t => t.documentTypes.includes('CV'))?.id
      ?? 'cv-modern-professional';
  }

  pdfFilename(document: StructuredDocument | null): string {
    if (!document) return 'document.pdf';
    return document.documentType === 'CV' ? 'angled-cv.pdf' : 'application.pdf';
  }

  setEditorContent(text: string): void {
    this.currentContent = text;
    // Use innerHTML with preserved line breaks for the contenteditable div
    this.editorHtml = editorHtml(text);
  }
}
