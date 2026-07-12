import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { PromptApiService } from '../../core/api/prompt.api';
import { Application } from '../../core/models/application.model';
import { PromptTemplate } from '../../core/models/prompt-template.model';
import { StructuredDocument } from '../../core/models/structured-document.model';
import { ResumeDraftApiService } from '../resume-builder/services/resume-draft-api.service';
import { structuredDocToResumeData } from '../resume-builder/services/structured-doc-mapper';
import { INITIAL_SETTINGS, ResumeDraft } from '../resume-builder/models/resume-builder.models';
import { loadGenDefaults } from '../settings/settings.component';

const ANGLE_PROMPTS = [
  'More technical depth',
  'Lead with motion / craft',
  'Quantify everything',
  'Shorter — aim for one page',
];

/**
 * Angled-CV entry point: configure the generation (prompt, language, angle)
 * and hand the result to the CV Builder as a draft. Existing drafts for the
 * job are offered for direct opening — the builder is where CVs live;
 * template and styling are chosen there, not here.
 */
@Component({
  selector: 'app-angled-cv',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbToastComponent],
  templateUrl: './angled-cv.component.html',
})
export class AngledCvComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);
  private promptApi = inject(PromptApiService);
  private draftApi = inject(ResumeDraftApiService);

  loading = signal(true);
  loadError = signal('');
  generating = signal(false);
  opening = signal(false);
  toast = signal('');

  application = signal<Application | null>(null);
  /** CV Builder drafts already created for this job. */
  drafts = signal<ResumeDraft[]>([]);
  /** Latest generated CV for this job (offered as a draft without regenerating). */
  latestDoc = signal<StructuredDocument | null>(null);
  versionCount = signal(0);

  promptTemplates: PromptTemplate[] = [];
  selectedPromptId = signal<string | null>(null);
  selectedLanguage = signal(loadGenDefaults().lang);
  languages = ['English', 'Dansk'];
  customInstructions = '';
  anglePrompts = ANGLE_PROMPTS;

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

    const appId = this.route.snapshot.paramMap.get('id');
    if (!appId) {
      this.loadError.set('No application selected.');
      this.loading.set(false);
      return;
    }
    this.appsApi.getById(appId).subscribe({
      next: app => {
        this.application.set(app);
        this.loadExisting(app.jobId);
      },
      error: () => {
        this.loadError.set('Could not load this application.');
        this.loading.set(false);
      }
    });
  }

  private loadExisting(jobId: string): void {
    this.draftApi.getDrafts().subscribe({
      next: drafts => this.drafts.set(drafts.filter(d => d.jobId === jobId)),
      error: () => {}
    });
    this.jobsApi.getDocumentsForJob(jobId).subscribe({
      next: docs => {
        const cvs = docs
          .filter(d => d.documentType === 'CV' && d.structuredContent)
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        this.versionCount.set(cvs.length);
        if (cvs.length > 0) {
          try {
            this.latestDoc.set(JSON.parse(cvs[0].structuredContent!) as StructuredDocument);
          } catch { /* unparseable — generation still works */ }
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  addAngle(prompt: string): void {
    this.customInstructions = this.customInstructions
      ? `${this.customInstructions.trim().replace(/\.?$/, '.')} ${prompt}.`
      : `${prompt}.`;
  }

  /** Generate a fresh angled CV and open it in the CV Builder as a draft. */
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
      next: doc => this.createDraftAndOpen(doc, app),
      error: err => {
        this.generating.set(false);
        this.toast.set(err?.error?.message ?? 'CV generation failed — check your master CV and try again.');
      }
    });
  }

  /** Skip regeneration: turn the latest generated CV into a builder draft. */
  useLatest(): void {
    const doc = this.latestDoc();
    const app = this.application();
    if (!doc || !app || this.opening()) return;
    this.opening.set(true);
    this.createDraftAndOpen(doc, app);
  }

  openDraft(draft: ResumeDraft): void {
    this.router.navigate(['/resume-builder', draft.id]);
  }

  private createDraftAndOpen(doc: StructuredDocument, app: Application): void {
    this.draftApi.createDraft({
      name: `Angled CV — ${app.jobCompanyName ?? 'job'}`,
      jobId: app.jobId,
      applicationId: app.id,
      resumeData: structuredDocToResumeData(doc),
      settings: { ...INITIAL_SETTINGS },
    }).subscribe({
      next: draft => this.router.navigate(['/resume-builder', draft.id]),
      error: () => {
        this.generating.set(false);
        this.opening.set(false);
        this.toast.set('Generated, but the draft could not be created — try again');
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

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }

  goToLetters(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) this.router.navigate(['/applications', appId, 'output']);
  }
}
