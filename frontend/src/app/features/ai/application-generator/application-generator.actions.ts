import { forkJoin } from 'rxjs';
import { AiApiService } from '../../../core/api/ai.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { PdfTemplatesApiService } from '../../../core/api/pdf-templates.api';
import { ApplicationStatus } from '../../../core/models/application.model';
import { StructuredDocument, StructuredDocumentSection } from '../../../core/models/structured-document.model';
import { runAction, resetFlagAfter } from '../../../shared/utils/async-ui';
import { copyText } from '../../../shared/utils/file-download';
import { cvPlainText, documentText } from './structured-document-content';
import type { ApplicationGeneratorComponent } from './application-generator.component';

export function generate(vm: ApplicationGeneratorComponent, aiApi: AiApiService): void {
  if (vm.form.invalid) return;
  vm.error = '';
  vm.applicationSaveMessage = '';
  vm.applicationDocument = null;
  vm.pairedCvDocument = null;
  vm.activeDocument = 'single';

  const v = vm.form.value;
  const selectedTemplate = vm.structuredTemplates.find(t => t.id === v.structuredTemplateId);

  if (v.documentType === 'CV') {
    runAction({
      action$: aiApi.generateStructuredCv({
      jobId: v.jobId || undefined,
      jobDescription: v.jobDescription || undefined,
      customInstructions: v.customInstructions || undefined,
      targetLanguage: v.targetLanguage || 'Danish',
      templateId: v.structuredTemplateId || selectedTemplate?.id,
      showProfileImage: v.showProfileImage ?? false,
      theme: vm.currentTheme()
      }),
      setLoading: value => vm.loading = value,
      setError: message => vm.error = message,
      errorMessage: error => (error as any)?.error?.message || 'CV generation failed. Please try again.',
      next: doc => {
        vm.structuredDocument = vm.withRenderOptions(doc);
        vm.currentContent = cvPlainText(doc);
        vm.editorHtml = '';
        vm.modelLabel = 'Structured CV engine';
        vm.showEditor = true;
        vm.chatHistory = [];
      }
    });
    return;
  }

  runAction({
    action$: aiApi.generateDocument({
    documentType: v.documentType as string,
    jobId: v.jobId || undefined,
    jobDescription: v.jobDescription || undefined,
    templateId: v.structuredTemplateId || selectedTemplate?.id || 'application-modern',
    customInstructions: v.customInstructions || undefined,
    motivationText: v.motivationText || undefined,
    targetLanguage: v.targetLanguage || 'Danish',
    showProfileImage: v.showProfileImage ?? false,
    theme: vm.currentTheme()
    }),
    setLoading: value => vm.loading = value,
    setError: message => vm.error = message,
    errorMessage: error => (error as any)?.error?.message || 'Generation failed. Please try again.',
    next: doc => {
      vm.structuredDocument = vm.withRenderOptions(doc);
      vm.currentContent = doc.bodyContent ?? '';
      vm.setEditorContent(vm.currentContent);
      vm.modelLabel = 'Structured document engine';
      vm.showEditor = true;
      vm.chatHistory = [];
    }
  });
}

export function generateApplicationSet(vm: ApplicationGeneratorComponent, aiApi: AiApiService): void {
  if (vm.form.invalid || vm.form.value.documentType === 'CV') return;
  vm.error = '';
  vm.applicationSaveMessage = '';

  const v = vm.form.value;
  const selectedTemplate = vm.structuredTemplates.find(t => t.id === v.structuredTemplateId);
  const base = {
    jobId: v.jobId || undefined,
    jobDescription: v.jobDescription || undefined,
    customInstructions: v.customInstructions || undefined,
    motivationText: v.motivationText || undefined,
    targetLanguage: v.targetLanguage || 'Danish',
    showProfileImage: v.showProfileImage ?? false,
    theme: vm.currentTheme()
  };

  runAction({
    action$: forkJoin({
    application: aiApi.generateDocument({
      ...base,
      documentType: v.documentType as string,
      templateId: v.structuredTemplateId || selectedTemplate?.id || 'application-modern'
    }),
    cv: aiApi.generateStructuredCv({
      ...base,
      templateId: vm.pairedCvTemplateId(selectedTemplate)
    })
    }),
    setLoading: value => vm.loading = value,
    setError: message => vm.error = message,
    errorMessage: error => (error as any)?.error?.message || 'Application and CV generation failed. Please try again.',
    next: ({ application, cv }) => {
      vm.applicationDocument = vm.withRenderOptions(application);
      vm.pairedCvDocument = vm.withRenderOptions(cv);
      vm.activeDocument = 'application';
      vm.structuredDocument = vm.applicationDocument;
      vm.loadCurrentDocumentIntoEditor();
      vm.modelLabel = 'Structured application and CV engines';
      vm.showEditor = true;
      vm.chatHistory = [];
    }
  });
}

export function sendChat(vm: ApplicationGeneratorComponent, aiApi: AiApiService): void {
  if (!vm.chatMessage.trim() || vm.refining) return;
  const userMsg = vm.chatMessage.trim();
  vm.chatMessage = '';
  vm.chatHistory = [...vm.chatHistory, { role: 'user', text: userMsg }];

  if (vm.structuredDocument?.documentType === 'CV') {
    vm.chatHistory = [...vm.chatHistory, {
      role: 'assistant',
      text: 'For structured CVs, add this as a custom instruction and regenerate so the backend can validate the JSON against your master profile.'
    }];
    return;
  }

  const v = vm.form.value;
  runAction({
    action$: aiApi.refine({
    currentContent: vm.currentContent,
    userMessage: userMsg,
    jobDescription: v.jobDescription || vm.jobDescription || undefined,
    targetLanguage: v.targetLanguage || 'Danish'
    }),
    setLoading: value => vm.refining = value,
    next: r => {
      vm.setEditorContent(r.refinedContent);
      if (vm.structuredDocument && vm.structuredDocument.documentType !== 'CV') {
        vm.structuredDocument = { ...vm.structuredDocument, bodyContent: r.refinedContent };
      }
      vm.chatHistory = [...vm.chatHistory, { role: 'assistant', text: 'Document updated.' }];
    },
    error: () => {
      vm.chatHistory = [...vm.chatHistory, { role: 'assistant', text: 'Failed to refine. Please try again.' }];
    }
  });
}

export function copyContent(vm: ApplicationGeneratorComponent): void {
  vm.persistActiveDocumentEdits();
  copyText(vm.currentContent).then(() => {
    vm.copied = true;
    resetFlagAfter(value => vm.copied = value);
  });
}

export function downloadPdf(vm: ApplicationGeneratorComponent, pdfApi: PdfTemplatesApiService): void {
  vm.persistActiveDocumentEdits();
  vm.downloadingPdf = true;
  vm.error = '';

  if (vm.structuredDocument) {
    runAction({
      action$: pdfApi.exportStructuredPdf(vm.structuredDocument),
      setLoading: value => vm.downloadingPdf = value,
      setError: message => vm.error = message,
      errorMessage: 'PDF generation failed. Please try again.',
      next: blob => vm.savePdfBlob(blob, vm.pdfFilename(vm.structuredDocument)),
    });
    return;
  }

  window.print();
  vm.downloadingPdf = false;
}

export function downloadCvPdf(vm: ApplicationGeneratorComponent, pdfApi: PdfTemplatesApiService): void {
  if (!vm.pairedCvDocument) return;
  vm.persistActiveDocumentEdits();
  vm.error = '';
  runAction({
    action$: pdfApi.exportStructuredPdf(vm.pairedCvDocument),
    setLoading: value => vm.downloadingPdf = value,
    setError: message => vm.error = message,
    errorMessage: 'CV PDF generation failed. Please try again.',
    next: blob => vm.savePdfBlob(blob, 'angled-cv.pdf'),
  });
}

export function saveToApplication(
  vm: ApplicationGeneratorComponent,
  aiApi: AiApiService,
  _applicationsApi: ApplicationsApiService,
  status: ApplicationStatus
): void {
  vm.persistActiveDocumentEdits();
  const documentToSave = vm.applicationDocument ?? vm.structuredDocument;
  if (!documentToSave) return;
  const jobId = vm.form.value.jobId;
  if (!jobId) {
    vm.error = 'Save needs a job record. Open the generator from a saved job, or add the job first.';
    return;
  }

  vm.savingApplication = true;
  vm.error = '';
  vm.applicationSaveMessage = '';
  runAction({
    action$: aiApi.saveStructuredDocument(documentToSave, jobId),
    setError: message => vm.error = message,
    errorMessage: 'Could not save the generated document. Please try again.',
    error: () => vm.savingApplication = false,
    next: savedDocument => saveOrAttachApplication(vm, aiApi, _applicationsApi, savedDocument, status, jobId),
  });
}

function saveOrAttachApplication(
  vm: ApplicationGeneratorComponent,
  aiApi: AiApiService,
  applicationsApi: ApplicationsApiService,
  document: StructuredDocument,
  status: ApplicationStatus,
  jobId: string
): void {
  document = vm.withRenderOptions(document);
  vm.rememberGeneratedDocument(document);
  const generatedDocumentId = document.generatedDocumentId;
  if (!generatedDocumentId) {
    vm.error = 'The generated document was saved without an id. Please regenerate and try again.';
    vm.savingApplication = false;
    return;
  }

  const content = documentText(document);
  if (vm.savedApplication) {
    const nextStatus = vm.savedApplication.status === 'APPLIED' ? 'APPLIED' : status;
    applicationsApi.attachGeneratedDocument(
      vm.savedApplication.id,
      generatedDocumentId,
      content,
      nextStatus
    ).subscribe({
      next: app => attachPairedCvIfPresent(vm, aiApi, applicationsApi, app, nextStatus),
      error: () => {
        vm.error = 'Could not attach this document to the application.';
        vm.savingApplication = false;
      }
    });
    return;
  }

  applicationsApi.create({
    jobId,
    generatedDocumentId,
    status,
    coverLetterText: document.documentType === 'COVER_LETTER' ? content : undefined,
    applicationText: document.documentType === 'APPLICATION_TEXT' ? content : undefined,
    recruiterMessage: document.documentType === 'RECRUITER_MESSAGE' || document.documentType === 'FOLLOW_UP_MESSAGE' ? content : undefined,
    matchScore: document.atsReport?.score,
    notes: status === 'APPLIED' ? 'Marked applied from generator.' : 'Draft saved from generator.'
  }).subscribe({
    next: app => attachPairedCvIfPresent(vm, aiApi, applicationsApi, app, status),
    error: () => {
      vm.error = 'Could not create the application record.';
      vm.savingApplication = false;
    }
  });
}

function attachPairedCvIfPresent(
  vm: ApplicationGeneratorComponent,
  aiApi: AiApiService,
  applicationsApi: ApplicationsApiService,
  app: NonNullable<ApplicationGeneratorComponent['savedApplication']>,
  status: ApplicationStatus
): void {
  if (!vm.pairedCvDocument) {
    finishApplicationSave(vm, app, status);
    return;
  }

  aiApi.saveStructuredDocument(vm.pairedCvDocument, app.jobId).subscribe({
    next: savedCv => {
      vm.pairedCvDocument = vm.withRenderOptions(savedCv);
      if (!savedCv.generatedDocumentId) {
        finishApplicationSave(vm, app, status);
        return;
      }
      applicationsApi.attachGeneratedDocument(
        app.id,
        savedCv.generatedDocumentId,
        cvPlainText(savedCv),
        app.status
      ).subscribe({
        next: updated => finishApplicationSave(vm, updated, status),
        error: () => {
          vm.error = 'Application saved, but the angled CV could not be attached.';
          finishApplicationSave(vm, app, status);
        }
      });
    },
    error: () => {
      vm.error = 'Application saved, but the angled CV could not be saved.';
      finishApplicationSave(vm, app, status);
    }
  });
}

function finishApplicationSave(
  vm: ApplicationGeneratorComponent,
  app: NonNullable<ApplicationGeneratorComponent['savedApplication']>,
  status: ApplicationStatus
): void {
  vm.savedApplication = app;
  vm.applicationSaveMessage = status === 'APPLIED'
    ? 'Application marked as applied and the generated document is attached.'
    : 'Draft saved and the generated document is attached.';
  vm.savingApplication = false;
}

export function moveSection(vm: ApplicationGeneratorComponent, index: number, direction: -1 | 1): void {
  if (!vm.structuredDocument) return;
  const sections = [...(vm.structuredDocument.sections ?? [])];
  const target = index + direction;
  if (target < 0 || target >= sections.length) return;
  [sections[index], sections[target]] = [sections[target], sections[index]];
  vm.structuredDocument = { ...vm.structuredDocument, sections };
  vm.currentContent = cvPlainText(vm.structuredDocument);
  vm.persistActiveDocumentEdits();
}

export function removeSection(vm: ApplicationGeneratorComponent, index: number): void {
  if (!vm.structuredDocument) return;
  const sections = [...(vm.structuredDocument.sections ?? [])];
  sections.splice(index, 1);
  vm.structuredDocument = { ...vm.structuredDocument, sections };
  vm.currentContent = cvPlainText(vm.structuredDocument);
  vm.persistActiveDocumentEdits();
}

export function addCustomSection(vm: ApplicationGeneratorComponent, heading: string, body: string): void {
  if (!vm.structuredDocument || !heading.trim()) return;
  const section: StructuredDocumentSection = {
    id: `custom-${Date.now()}`,
    type: 'custom',
    heading: heading.trim(),
    body: body.trim(),
    items: []
  };
  vm.structuredDocument = {
    ...vm.structuredDocument,
    sections: [...(vm.structuredDocument.sections ?? []), section]
  };
  vm.currentContent = cvPlainText(vm.structuredDocument);
  vm.persistActiveDocumentEdits();
}
