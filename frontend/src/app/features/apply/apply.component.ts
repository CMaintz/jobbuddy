import { Component, HostListener, OnInit, effect, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of, switchMap, catchError, map, tap, forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { AiApiService, GenerateDocumentRequest } from '../../core/api/ai.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { PromptApiService } from '../../core/api/prompt.api';
import { Application } from '../../core/models/application.model';
import { Job } from '../../core/models/job.model';
import { PromptTemplate } from '../../core/models/prompt-template.model';
import { loadGenDefaults } from '../settings/settings.component';

const FORMAT_TO_DOC_TYPE: Record<string, string> = {
  'application': 'APPLICATION_TEXT',
  'cover-letter': 'COVER_LETTER',
  'short-pitch': 'RECRUITER_MESSAGE',
};

const FORMAT_TO_OUTPUT_KEY: Record<string, string> = {
  'application': 'app',
  'cover-letter': 'cl',
  'short-pitch': 'dm',
};

const FORMAT_TO_PROMPT_CATEGORY: Record<string, string> = {
  'application': 'APPLICATION',
  'cover-letter': 'COVER_LETTER',
  'short-pitch': 'RECRUITER_MESSAGE',
  'cv': 'CV_TAILORING',
};

const DRAFT_KEY = 'jb-apply-draft';
const DRAFT_MAX_AGE_MS = 7 * 24 * 3600_000;

/** Handoff key written by the job-capture browser extension (see extension/). */
const CAPTURE_KEY = 'jb-captured-job';
const CAPTURE_MAX_AGE_MS = 10 * 60_000;

interface CapturedJob {
  savedAt: number;
  title?: string;
  company?: string;
  location?: string;
  description?: string;
  url?: string;
}

interface ApplyDraft {
  savedAt: number;
  mode: 'paste' | 'url' | 'manual' | 'unsolicited';
  company: string;
  role: string;
  location: string;
  jd: string;
  jobUrl: string;
  customInstructions: string;
  format: string;
  voice: string;
  language: string;
  promptId: string | null;
}

@Component({
  selector: 'app-apply',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent],
  templateUrl: './apply.component.html',
  styleUrls: ['./apply.component.css']
})
export class ApplyComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);
  private promptApi = inject(PromptApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  phase = signal<'input' | 'generating'>('input');
  private busy = signal(false);
  mode = signal<'paste' | 'url' | 'manual' | 'unsolicited'>('paste');
  selectedFormat = signal('application');
  selectedVoice = signal('Warm');
  selectedLanguage = signal('English');
  selectedPromptId = signal<string | null>(null);
  activeStep = signal(0);
  generateError = signal('');

  /** Hostname the extension captured the posting from — shows a banner when set. */
  capturedFrom = signal('');
  /** Job preselected via ?jobId= (from job details / feed) */
  presetJob = signal<Job | null>(null);
  /** Existing in-progress application for the preset job — offer to resume instead of regenerating */
  resumableApp = signal<Application | null>(null);

  promptTemplates: PromptTemplate[] = [];

  /** Templates matching the selected format's category, plus GENERAL ones. */
  get relevantPromptTemplates(): PromptTemplate[] {
    const category = FORMAT_TO_PROMPT_CATEGORY[this.selectedFormat()];
    return this.promptTemplates.filter(t =>
      !t.category || t.category === 'GENERAL' || t.category === category);
  }

  company = '';
  role = '';
  location = '';
  jd = '';
  jobUrl = '';
  customInstructions = '';

  sourceOptions = [
    { mode: 'paste' as const, icon: 'copy', label: 'apply.source.paste.label', sub: 'apply.source.paste.sub' },
    { mode: 'url' as const, icon: 'link', label: 'apply.source.url.label', sub: 'apply.source.url.sub' },
    { mode: 'manual' as const, icon: 'edit', label: 'apply.source.manual.label', sub: 'apply.source.manual.sub' },
    { mode: 'unsolicited' as const, icon: 'mail', label: 'apply.source.unsolicited.label', sub: 'apply.source.unsolicited.sub' },
  ];

  formats = [
    { key: 'application', label: 'apply.fmt.application' },
    { key: 'cover-letter', label: 'apply.fmt.coverLetter' },
    { key: 'short-pitch', label: 'apply.fmt.shortPitch' },
    { key: 'cv', label: 'apply.fmt.tailoredCv' },
  ];

  voices = ['Direct', 'Warm', 'Formal'];
  languages = ['English', 'Dansk'];

  generatingSteps = [
    { label: 'apply.step.prepareJob', note: '' },
    { label: 'apply.step.createApplication', note: '' },
    { label: 'apply.step.draftDocument', note: '' },
    { label: 'apply.step.finishUp', note: '' },
  ];

  get wordCount(): number {
    return this.jd.trim() ? this.jd.trim().split(/\s+/).length : 0;
  }

  get selectedFormatLabel(): string {
    return this.formats.find(f => f.key === this.selectedFormat())?.label ?? 'apply.fmt.application';
  }

  constructor() {
    // Auto-save: this effect covers the signal-backed choices; the template's
    // (input) hook covers the typed fields. Both funnel into persistDraft().
    effect(() => {
      const tracked = [this.mode(), this.selectedFormat(), this.selectedVoice(),
        this.selectedLanguage(), this.selectedPromptId()];
      void tracked;
      if (this.restored) this.persistDraft();
    });
  }

  /** Guards against the initial effect run persisting defaults over a stored draft. */
  private restored = false;

  persistDraft(): void {
    const draft: ApplyDraft = {
      savedAt: Date.now(),
      mode: this.mode(),
      company: this.company,
      role: this.role,
      location: this.location,
      jd: this.jd,
      jobUrl: this.jobUrl,
      customInstructions: this.customInstructions,
      format: this.selectedFormat(),
      voice: this.selectedVoice(),
      language: this.selectedLanguage(),
      promptId: this.selectedPromptId(),
    };
    try { localStorage.setItem(DRAFT_KEY, JSON.stringify(draft)); } catch { /* storage full/blocked */ }
  }

  /** Restores an unfinished form. Job fields are skipped when a job is preset via ?jobId=. */
  private restoreDraft(hasPresetJob: boolean): void {
    try {
      const raw = localStorage.getItem(DRAFT_KEY);
      if (!raw) return;
      const draft = JSON.parse(raw) as ApplyDraft;
      if (!draft.savedAt || Date.now() - draft.savedAt > DRAFT_MAX_AGE_MS) {
        localStorage.removeItem(DRAFT_KEY);
        return;
      }
      if (!hasPresetJob) {
        this.mode.set(draft.mode ?? 'paste');
        this.company = draft.company ?? '';
        this.role = draft.role ?? '';
        this.location = draft.location ?? '';
        this.jd = draft.jd ?? '';
        this.jobUrl = draft.jobUrl ?? '';
      }
      this.customInstructions = draft.customInstructions ?? '';
      if (this.formats.some(f => f.key === draft.format)) this.selectedFormat.set(draft.format);
      if (this.voices.includes(draft.voice)) this.selectedVoice.set(draft.voice);
      if (this.languages.includes(draft.language)) this.selectedLanguage.set(draft.language);
      this.selectedPromptId.set(draft.promptId ?? null);
    } catch { /* corrupt draft — ignore */ }
  }

  private clearDraft(): void {
    try { localStorage.removeItem(DRAFT_KEY); } catch { /* ignore */ }
  }

  ngOnInit(): void {
    const defaults = loadGenDefaults();
    if (this.voices.includes(defaults.voice)) this.selectedVoice.set(defaults.voice);
    if (this.languages.includes(defaults.lang)) this.selectedLanguage.set(defaults.lang);

    this.promptApi.getAll().subscribe({
      next: templates => this.promptTemplates = templates,
      error: () => {} // prompt templates are optional
    });

    const jobId = this.route.snapshot.queryParamMap.get('jobId');
    const captured = !jobId && this.consumeCapture();
    this.restoreDraft(!!jobId || captured);

    // Explicit query params outrank the restored draft
    const format = this.route.snapshot.queryParamMap.get('format');
    if (format && this.formats.some(f => f.key === format)) this.selectedFormat.set(format);

    if (jobId) this.loadPresetJob(jobId);
    this.restored = true;
  }

  /** The extension's content script fires this after writing the capture, which can land post-init. */
  @HostListener('window:jb-capture-ready')
  onCaptureReady(): void {
    this.consumeCapture();
  }

  /** Reads and clears a job captured by the browser extension; prefills the form when fresh. */
  private consumeCapture(): boolean {
    try {
      const raw = localStorage.getItem(CAPTURE_KEY);
      if (!raw) return false;
      localStorage.removeItem(CAPTURE_KEY);
      const capture = JSON.parse(raw) as CapturedJob;
      if (!capture.savedAt || Date.now() - capture.savedAt > CAPTURE_MAX_AGE_MS) return false;
      if (!capture.title && !capture.description) return false;
      this.mode.set('paste');
      this.role = capture.title ?? '';
      this.company = capture.company ?? '';
      this.location = capture.location ?? '';
      this.jd = capture.description ?? '';
      this.jobUrl = capture.url ?? '';
      try {
        this.capturedFrom.set(capture.url ? new URL(capture.url).hostname : 'apply.yourBrowser');
      } catch {
        this.capturedFrom.set('apply.yourBrowser');
      }
      return true;
    } catch {
      return false;
    }
  }

  private loadPresetJob(jobId: string): void {
    this.jobsApi.getById(jobId).subscribe({
      next: job => {
        this.presetJob.set(job);
        this.company = job.companyName ?? '';
        this.role = job.title ?? '';
        this.location = job.location ?? '';
        this.jd = job.descriptionClean ?? '';
        this.mode.set('manual');
        // Danish job listing? Default output language to Dansk
        if (job.languages?.some(l => l.toLowerCase().startsWith('da'))) this.selectedLanguage.set('Dansk');
      },
      error: () => this.generateError.set('apply.error.loadJob')
    });

    this.appsApi.getAll().subscribe({
      next: apps => {
        const existing = apps.find(a => a.jobId === jobId && a.status === 'PREPARING');
        if (existing) this.resumableApp.set(existing);
      },
      error: () => {}
    });
  }

  resume(): void {
    const app = this.resumableApp();
    if (app) this.router.navigate(['/applications', app.id, 'output']);
  }

  isReady(): boolean {
    if (!this.company.trim() || !this.role.trim()) return false;
    // Unsolicited: there is no posting, so the description is optional context.
    return this.mode() === 'unsolicited' || this.jd.trim().length > 40;
  }

  generate(): void {
    if (!this.isReady() || this.busy()) return;
    this.busy.set(true);
    this.generateError.set('');
    this.phase.set('generating');
    this.activeStep.set(0);

    // Step 1: resolve the job (preset > lookup by URL > create manually)
    this.resolveJob().pipe(
      tap(() => this.activeStep.set(1)),
      // Step 2: reuse an in-progress application or create one
      switchMap(job => this.resolveApplication(job)),
      tap(() => this.activeStep.set(2)),
      // Step 3: generate the document (the long step).
      // The tailored CV is configured & generated on its own screen — hand off after step 2.
      switchMap(app => {
        if (this.selectedFormat() === 'cv') return of(app);
        const req: GenerateDocumentRequest = {
          jobId: app.jobId,
          documentType: this.documentTypeForRequest(),
          targetLanguage: this.selectedLanguage() === 'Dansk' ? 'da' : 'en',
          promptTemplateId: this.selectedPromptId() ?? undefined,
          customInstructions: this.buildInstructions(),
        };
        return this.aiApi.generateDocument(req).pipe(map(() => app));
      }),
      tap(() => this.activeStep.set(3)),
    ).subscribe({
      next: app => {
        this.activeStep.set(4);
        this.clearDraft();
        if (this.selectedFormat() === 'cv') {
          this.router.navigate(['/applications', app.id, 'cv'], {
            queryParams: {
              promptId: this.selectedPromptId() ?? undefined,
              lang: this.selectedLanguage(),
              instructions: this.customInstructions.trim() || undefined,
            }
          });
        } else {
          this.router.navigate(['/applications', app.id, 'output'],
            { queryParams: { format: this.outputKeyForRequest() } });
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.generateError.set(err?.error?.message
          ?? 'apply.error.generateFailed');
      }
    });
  }

  cancelGenerating(): void {
    this.busy.set(false);
    this.phase.set('input');
    this.generateError.set('');
  }

  /**
   * One-shot: generate a tailored CV AND a cover letter (with defaults) in parallel, then land on
   * the combined pack review. Reuses the same job/application resolution as generate().
   */
  quickApply(): void {
    if (!this.isReady() || this.busy()) return;
    this.busy.set(true);
    this.generateError.set('');
    this.phase.set('generating');
    this.activeStep.set(0);

    const lang = this.selectedLanguage() === 'Dansk' ? 'da' : 'en';
    const instructions = this.buildInstructions();
    const coverType = this.mode() === 'unsolicited' ? 'UNSOLICITED_APPLICATION' : 'COVER_LETTER';

    this.resolveJob().pipe(
      tap(() => this.activeStep.set(1)),
      switchMap(job => this.resolveApplication(job)),
      tap(() => this.activeStep.set(2)),
      switchMap(app => forkJoin([
        this.aiApi.generateStructuredCv({
          jobId: app.jobId, targetLanguage: lang,
          templateId: loadGenDefaults().cvTemplate || undefined,
          customInstructions: instructions || undefined,
        }),
        this.aiApi.generateDocument({
          jobId: app.jobId, documentType: coverType, targetLanguage: lang,
          customInstructions: instructions || undefined,
        }),
      ]).pipe(map(() => app))),
      tap(() => this.activeStep.set(3)),
    ).subscribe({
      next: app => {
        this.activeStep.set(4);
        this.clearDraft();
        this.router.navigate(['/applications', app.id, 'pack'], { queryParams: { jobId: app.jobId } });
      },
      error: (err) => {
        this.busy.set(false);
        this.generateError.set(err?.error?.message ?? 'apply.error.generateFailed');
      }
    });
  }

  /** Letter formats become an unsolicited letter when there is no posting; pitch/CV stay as-is. */
  private documentTypeForRequest(): string {
    const format = this.selectedFormat();
    if (this.mode() === 'unsolicited' && (format === 'application' || format === 'cover-letter')) {
      return 'UNSOLICITED_APPLICATION';
    }
    return FORMAT_TO_DOC_TYPE[format] ?? 'APPLICATION_TEXT';
  }

  private outputKeyForRequest(): string {
    const format = this.selectedFormat();
    if (this.mode() === 'unsolicited' && (format === 'application' || format === 'cover-letter')) {
      return 'ua';
    }
    return FORMAT_TO_OUTPUT_KEY[format] ?? 'app';
  }

  private resolveJob(): Observable<Job> {
    const preset = this.presetJob();
    if (preset) return of(preset);

    const createManual = () => this.jobsApi.addManual({
      title: this.role.trim(),
      companyName: this.company.trim(),
      description: this.mode() === 'unsolicited' && !this.jd.trim()
        ? `Unsolicited application target: ${this.role.trim()} at ${this.company.trim()} — no posted vacancy.`
        : this.jd.trim(),
      url: this.jobUrl.trim() || undefined,
      location: this.location.trim() || undefined,
    });

    if (this.mode() === 'url' && this.jobUrl.trim()) {
      return this.jobsApi.lookupByUrl(this.jobUrl.trim()).pipe(
        catchError(() => createManual())
      );
    }
    return createManual();
  }

  private resolveApplication(job: Job): Observable<Application> {
    return this.appsApi.getAll().pipe(
      switchMap(apps => {
        const existing = apps.find(a => a.jobId === job.id && a.status === 'PREPARING');
        return existing
          ? of(existing)
          : this.appsApi.create({ jobId: job.id, status: 'PREPARING' });
      })
    );
  }

  private buildInstructions(): string {
    const parts = [`Voice: ${this.selectedVoice()}.`];
    if (this.customInstructions.trim()) parts.push(this.customInstructions.trim());
    return parts.join(' ');
  }
}
