import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
import { structuredDocToResumeData, structuredDocToLayout } from '../resume-builder/services/structured-doc-mapper';
import { INITIAL_SETTINGS, ResumeDraft } from '../resume-builder/models/resume-builder.models';
import { loadGenDefaults } from '../settings/settings.component';

const TAILOR_PROMPTS = [
  'tailoredCv.tailor.depth',
  'tailoredCv.tailor.craft',
  'tailoredCv.tailor.quantify',
  'tailoredCv.tailor.shorter',
];

/**
 * Tailored-CV entry point: configure the generation (prompt, language, focus)
 * and hand the result to the CV Builder as a draft. Existing drafts for the
 * job are offered for direct opening — the builder is where CVs live;
 * template and styling are chosen there, not here.
 */
@Component({
  selector: 'app-tailored-cv',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, JbToastComponent],
  templateUrl: './tailored-cv.component.html',
})
export class TailoredCvComponent implements OnInit {
  private translate = inject(TranslateService);
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
  tailorPrompts = TAILOR_PROMPTS;

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
      this.loadError.set(this.translate.instant('output.noAppSelected'));
      this.loading.set(false);
      return;
    }
    this.appsApi.getById(appId).subscribe({
      next: app => {
        this.application.set(app);
        this.loadExisting(app.jobId);
      },
      error: () => {
        this.loadError.set(this.translate.instant('output.loadAppFailed'));
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

  addTailorPrompt(promptOrKey: string): void {
    const prompt = this.translate.instant(promptOrKey);
    this.customInstructions = this.customInstructions
      ? `${this.customInstructions.trim().replace(/\.?$/, '.')} ${prompt}.`
      : `${prompt}.`;
  }

  /** Generate a fresh tailored CV and open it in the CV Builder as a draft. */
  generate(): void {
    const app = this.application();
    if (!app || this.generating()) return;
    this.generating.set(true);
    this.aiApi.generateStructuredCv({
      jobId: app.jobId,
      promptTemplateId: this.selectedPromptId() ?? undefined,
      customInstructions: this.customInstructions.trim() || undefined,
      targetLanguage: this.selectedLanguage() === 'Dansk' ? 'da' : 'en',
      showProfileImage: true, // master CV photo carries onto tailored CVs automatically
      lengthPreference: loadGenDefaults().length?.toUpperCase(),
    }).subscribe({
      next: doc => this.createDraftAndOpen(doc, app),
      error: err => {
        this.generating.set(false);
        this.toast.set(err?.error?.message ?? this.translate.instant('tailoredCv.toast.genFailed'));
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
      name: this.translate.instant('tailoredCv.draftName', { company: app.jobCompanyName ?? this.translate.instant('tailoredCv.jobFallback') }),
      jobId: app.jobId,
      applicationId: app.id,
      resumeData: structuredDocToResumeData(doc),
      // Career-stage-aware section order chosen server-side becomes the draft's default layout.
      settings: { ...INITIAL_SETTINGS, ...structuredDocToLayout(doc) },
    }).subscribe({
      next: draft => this.router.navigate(['/resume-builder', draft.id]),
      error: () => {
        this.generating.set(false);
        this.opening.set(false);
        this.toast.set(this.translate.instant('tailoredCv.toast.draftFailed'));
      }
    });
  }

  ageLabel(iso?: string): string {
    if (!iso) return '';
    const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 1) return this.translate.instant('time.justNow');
    if (mins < 60) return this.translate.instant('time.minutesAgo', { n: mins });
    const hours = Math.floor(mins / 60);
    if (hours < 24) return this.translate.instant('time.hoursAgo', { n: hours });
    return this.translate.instant('time.daysAgo', { n: Math.floor(hours / 24) });
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
