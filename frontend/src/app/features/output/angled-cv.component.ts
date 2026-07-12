import { Component, ElementRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { StructuredDocumentRendererComponent } from '../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { PromptApiService } from '../../core/api/prompt.api';
import { Application } from '../../core/models/application.model';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { PromptTemplate } from '../../core/models/prompt-template.model';
import { AtsReport, StructuredDocument } from '../../core/models/structured-document.model';
import { ResumeDraftApiService } from '../resume-builder/services/resume-draft-api.service';
import { PdfExportService } from '../resume-builder/services/pdf-export.service';
import { structuredDocToResumeData } from '../resume-builder/services/structured-doc-mapper';
import { INITIAL_SETTINGS } from '../resume-builder/models/resume-builder.models';
import { loadGenDefaults } from '../settings/settings.component';

const ANGLE_PROMPTS = [
  'More technical depth',
  'Lead with motion / craft',
  'Quantify everything',
  'Shorter — aim for one page',
];

@Component({
  selector: 'app-angled-cv',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, DiffViewerComponent, StructuredDocumentRendererComponent],
  templateUrl: './angled-cv.component.html',
})
export class AngledCvComponent implements OnInit {
  @ViewChild('cvEl') cvEl!: ElementRef<HTMLElement>;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);
  private promptApi = inject(PromptApiService);
  private draftApi = inject(ResumeDraftApiService);
  private pdfExport = inject(PdfExportService);

  loading = signal(true);
  loadError = signal('');
  generating = signal(false);
  openingDraft = signal(false);
  downloading = signal(false);
  toast = signal('');
  mobileShowSidebar = signal(false);

  application = signal<Application | null>(null);
  cvDocuments = signal<GeneratedDocument[]>([]);
  /** Non-CV documents (letters, pitches) for the cross-link to the output screen. */
  letterCount = signal(0);
  promptTemplates: PromptTemplate[] = [];

  selectedPromptId = signal<string | null>(null);
  selectedLanguage = signal(loadGenDefaults().lang);
  languages = ['English', 'Dansk'];
  customInstructions = '';
  anglePrompts = ANGLE_PROMPTS;

  /** Latest generated CV for this job, parsed into a renderable structured document. */
  activeCv = computed<StructuredDocument | null>(() => {
    const doc = this.cvDocuments()[0];
    if (!doc?.structuredContent) return null;
    try {
      return JSON.parse(doc.structuredContent) as StructuredDocument;
    } catch {
      return null;
    }
  });

  atsReport = computed<AtsReport | null>(() => this.activeCv()?.atsReport ?? null);

  versionCount = computed(() => this.cvDocuments().length);

  /** Master (non-tailored) CV, for the what-changed diff. */
  masterCv = signal<StructuredDocument | null>(null);

  /** Profile text before/after tailoring — the headline change worth diffing. */
  profileDiff = computed<{ before: string; after: string } | null>(() => {
    const before = this.sectionBody(this.masterCv(), 'profile');
    const after = this.sectionBody(this.activeCv(), 'profile');
    if (!before || !after || before === after) return null;
    return { before, after };
  });

  private sectionBody(doc: StructuredDocument | null, type: string): string {
    return doc?.sections?.find(s => s.type === type)?.body ?? '';
  }

  ngOnInit(): void {
    // Config handed off from the Apply screen, if any
    const qp = this.route.snapshot.queryParamMap;
    if (qp.get('promptId')) this.selectedPromptId.set(qp.get('promptId'));
    if (qp.get('lang') && this.languages.includes(qp.get('lang')!)) this.selectedLanguage.set(qp.get('lang')!);
    if (qp.get('instructions')) this.customInstructions = qp.get('instructions')!;

    this.promptApi.getAll().subscribe({
      next: templates => this.promptTemplates = templates.filter(t =>
        !t.category || t.category === 'GENERAL' || t.category === 'CV_TAILORING'),
      error: () => {}
    });

    this.aiApi.getCvRenderModel().subscribe({
      next: master => this.masterCv.set(master),
      error: () => {} // diff panel simply hides without it
    });

    const appId = this.route.snapshot.paramMap.get('id');
    if (!appId) {
      this.loadError.set('No application selected.');
      this.loading.set(false);
      return;
    }
    this.appsApi.getById(appId).subscribe({
      next: app => {
        this.application.set(app);
        this.loadCvDocuments(app.jobId);
      },
      error: () => {
        this.loadError.set('Could not load this application.');
        this.loading.set(false);
      }
    });
  }

  private loadCvDocuments(jobId: string): void {
    this.jobsApi.getDocumentsForJob(jobId).subscribe({
      next: docs => {
        this.cvDocuments.set(docs
          .filter(d => d.documentType === 'CV')
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
        this.letterCount.set(docs.filter(d => d.documentType !== 'CV').length);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load generated CVs.');
        this.loading.set(false);
      }
    });
  }

  addAngle(prompt: string): void {
    this.customInstructions = this.customInstructions
      ? `${this.customInstructions.trim().replace(/\.?$/, '.')} ${prompt}.`
      : `${prompt}.`;
  }

  generate(): void {
    const app = this.application();
    if (!app || this.generating()) return;
    this.generating.set(true);
    this.aiApi.generateStructuredCv({
      jobId: app.jobId,
      promptTemplateId: this.selectedPromptId() ?? undefined,
      customInstructions: this.customInstructions.trim() || undefined,
      targetLanguage: this.selectedLanguage() === 'Dansk' ? 'da' : 'en',
      showProfileImage: true, // master CV photo carries onto angled CVs automatically
    }).subscribe({
      next: () => {
        this.generating.set(false);
        this.loadCvDocuments(app.jobId);
      },
      error: (err) => {
        this.generating.set(false);
        this.toast.set(err?.error?.message ?? 'CV generation failed — check your master CV and try again.');
      }
    });
  }

  /** Convert the generated CV into an editable resume-builder draft. */
  editInBuilder(): void {
    const doc = this.activeCv();
    const app = this.application();
    if (!doc || this.openingDraft()) return;
    this.openingDraft.set(true);
    this.draftApi.createDraft({
      name: `Angled CV — ${app?.jobCompanyName ?? 'job'}`,
      jobId: app?.jobId,
      resumeData: structuredDocToResumeData(doc),
      settings: { ...INITIAL_SETTINGS },
    }).subscribe({
      next: draft => this.router.navigate(['/resume-builder', draft.id]),
      error: () => {
        this.openingDraft.set(false);
        this.toast.set('Could not create the draft');
      }
    });
  }

  ageLabel(iso?: string): string {
    if (!iso) return '';
    const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }

  goToLetters(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) this.router.navigate(['/applications', appId, 'output']);
  }

  downloadPdf(): void {
    if (!this.cvEl?.nativeElement || this.downloading()) return;
    this.downloading.set(true);
    const app = this.application();
    const filename = app
      ? `${app.jobCompanyName ?? 'cv'} - Angled CV`
      : 'Angled CV';
    this.pdfExport.download(this.cvEl.nativeElement, filename).finally(() => this.downloading.set(false));
  }

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }
}
