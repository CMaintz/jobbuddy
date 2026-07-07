import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap, catchError, map, tap } from 'rxjs';
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

@Component({
  selector: 'app-apply',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent],
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
  mode = signal<'paste' | 'url' | 'manual'>('paste');
  selectedFormat = signal('application');
  selectedVoice = signal('Warm');
  selectedLanguage = signal('English');
  selectedPromptId = signal<string | null>(null);
  activeStep = signal(0);
  generateError = signal('');

  /** Job preselected via ?jobId= (from job details / feed) */
  presetJob = signal<Job | null>(null);
  /** Existing in-progress application for the preset job — offer to resume instead of regenerating */
  resumableApp = signal<Application | null>(null);

  promptTemplates: PromptTemplate[] = [];

  company = '';
  role = '';
  location = '';
  jd = '';
  jobUrl = '';
  customInstructions = '';

  sourceOptions = [
    { mode: 'paste' as const, icon: 'copy', label: 'Paste JD text', sub: 'Paste from clipboard' },
    { mode: 'url' as const, icon: 'link', label: 'Paste URL', sub: 'We look it up in your feed' },
    { mode: 'manual' as const, icon: 'edit', label: 'Enter manually', sub: 'Type company, role & JD' },
  ];

  formats = [
    { key: 'application', label: 'Application' },
    { key: 'cover-letter', label: 'Cover letter' },
    { key: 'short-pitch', label: 'Short pitch' },
  ];

  voices = ['Direct', 'Warm', 'Formal'];
  languages = ['English', 'Dansk'];

  generatingSteps = [
    { label: 'Prepare job', note: '' },
    { label: 'Create application', note: '' },
    { label: 'Draft document', note: '' },
    { label: 'Finish up', note: '' },
  ];

  get wordCount(): number {
    return this.jd.trim() ? this.jd.trim().split(/\s+/).length : 0;
  }

  ngOnInit(): void {
    const defaults = loadGenDefaults();
    if (this.voices.includes(defaults.voice)) this.selectedVoice.set(defaults.voice);
    if (this.languages.includes(defaults.lang)) this.selectedLanguage.set(defaults.lang);

    this.promptApi.getAll().subscribe({
      next: templates => this.promptTemplates = templates,
      error: () => {} // prompt templates are optional
    });

    const format = this.route.snapshot.queryParamMap.get('format');
    if (format && this.formats.some(f => f.key === format)) this.selectedFormat.set(format);

    const jobId = this.route.snapshot.queryParamMap.get('jobId');
    if (jobId) this.loadPresetJob(jobId);
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
      error: () => this.generateError.set('Could not load the selected job — you can still paste the description.')
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
    return !!(this.company.trim() && this.role.trim() && this.jd.trim().length > 40);
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
      // Step 3: generate the document (the long step)
      switchMap(app => {
        const req: GenerateDocumentRequest = {
          jobId: app.jobId,
          documentType: FORMAT_TO_DOC_TYPE[this.selectedFormat()] ?? 'APPLICATION_TEXT',
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
        this.router.navigate(['/applications', app.id, 'output'],
          { queryParams: { format: FORMAT_TO_OUTPUT_KEY[this.selectedFormat()] ?? 'app' } });
      },
      error: (err) => {
        this.busy.set(false);
        this.generateError.set(err?.error?.message
          ?? 'Generation failed. Check that your master CV has content and try again.');
      }
    });
  }

  cancelGenerating(): void {
    this.busy.set(false);
    this.phase.set('input');
    this.generateError.set('');
  }

  private resolveJob(): Observable<Job> {
    const preset = this.presetJob();
    if (preset) return of(preset);

    const createManual = () => this.jobsApi.addManual({
      title: this.role.trim(),
      companyName: this.company.trim(),
      description: this.jd.trim(),
      url: this.mode() === 'url' ? this.jobUrl.trim() || undefined : undefined,
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
