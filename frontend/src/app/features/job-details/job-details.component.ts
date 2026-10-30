import { ChangeDetectorRef, Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { StatusChipComponent, stageLabelKey } from '../../shared/components/status-chip/status-chip.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { NotesApiService } from '../../core/api/notes.api';
import { Note } from '../../core/models/note.model';
import { Job, JobRequirement } from '../../core/models/job.model';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { NotifyService } from '../../shared/services/notify.service';

const STAGE_FLOW: ApplicationStatus[] = ['SAVED', 'APPLIED', 'RECRUITER_CONTACT', 'INTERVIEW', 'OFFER'];


@Component({
  selector: 'app-job-details',
  imports: [
    CommonModule, RouterLink, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent,
    StatusChipComponent, CompanyMarkComponent, FitBarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './job-details.component.html'
})
export class JobDetailsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private notify = inject(NotifyService);
  private router = inject(Router);
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);
  private notesApi = inject(NotesApiService);
  private translate = inject(TranslateService);
  private cdr = inject(ChangeDetectorRef);

  loading = signal(true);
  job = signal<Job | null>(null);
  application = signal<Application | null>(null);
  documents = signal<GeneratedDocument[]>([]);
  // A plain field because the notes textarea binds it through ngModel; loads mark the view.
  notesText = '';
  notesStatus = signal('');
  saved = signal(false);
  reportedInactive = signal(false);
  similarJobs = signal<Job[]>([]);
  private note: Note | null = null;
  tab = signal<'jd' | 'activity' | 'notes'>('jd');
  mobileShowRail = signal(false);

  stageFlow = STAGE_FLOW;

  tabs = computed(() => [
    { key: 'jd' as const, label: 'jobDetails.tab.jd' },
    { key: 'activity' as const, label: 'jobDetails.tab.activity', count: this.activityEvents().length },
    { key: 'notes' as const, label: 'jobDetails.tab.notes', count: 0 },
  ]);

  openingCv = signal(false);

  generateActions = [
    { label: 'apply.fmt.application', icon: 'layers', format: 'application' },
    { label: 'apply.fmt.coverLetter', icon: 'doc', format: 'cover-letter' },
    { label: 'apply.fmt.shortPitch', icon: 'mail', format: 'short-pitch' },
    { label: 'apply.fmt.tailoredCv', icon: 'target', format: 'cv' },
  ];

  activityEvents = signal<{ time: string; what: string; who: string; dot: string }[]>([]);

  get salaryRange(): string {
    const job = this.job();
    if (!job?.salaryMin && !job?.salaryMax) return '';
    const cur = job.currency || '';
    if (job.salaryMin && job.salaryMax) return `${cur}${job.salaryMin}–${job.salaryMax}`;
    if (job.salaryMin) return `${cur}${job.salaryMin}+`;
    return this.translate.instant('jobDetails.upTo', { amount: `${cur}${job.salaryMax}` });
  }

  /**
   * The posting's asks, demands first. Listed rather than scored: only the skill asks are
   * something a keyword check can settle, and the rest are for the reader to judge.
   */
  get requirements(): JobRequirement[] {
    const all = this.job()?.requirements ?? [];
    return [...all.filter(r => r.tier === 'REQUIRED'), ...all.filter(r => r.tier !== 'REQUIRED')];
  }

  get keywords(): string[] {
    const job = this.job();
    return [
      ...(job?.technologies || []),
      ...(job?.skills || []),
      ...(job?.aiTags || []),
    ].slice(0, 12);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.loading.set(false); return; }
    this.load(id);
  }

  /** Navigating between jobs reuses this component — reset and reload manually. */
  openSimilar(id: string): void {
    this.router.navigate(['/jobs', id]);
    this.loading.set(true);
    this.job.set(null);
    this.application.set(null);
    this.documents.set([]);
    this.activityEvents.set([]);
    this.similarJobs.set([]);
    this.note = null;
    this.notesText = '';
    this.tab.set('jd');
    this.load(id);
  }

  private load(id: string): void {
    // Try loading as a job first
    this.jobsApi.getById(id).subscribe({
      next: (job) => {
        this.job.set(job);
        this.loading.set(false);
        this.buildActivity();
        this.loadJobExtras(job.id);
        // An application may already exist for this job (stepper, documents, recruiter email)
        this.appsApi.getAll().subscribe({
          next: apps => {
            const app = apps.find(a => a.jobId === job.id);
            if (app) {
              this.application.set(app);
              if (!this.notesText) this.setNotesText(app.notes || '');
              this.buildActivity();
            }
          },
          error: this.notify.onError('notify.loadFailed')
        });
        // Try to load documents for this job
        this.jobsApi.getDocumentsForJob(id).subscribe({
          next: (docs) => this.documents.set(docs),
          error: this.notify.onError('notify.loadFailed')
        });
      },
      error: () => {
        // Maybe the ID is an application ID — try loading application
        this.appsApi.getById(id).subscribe({
          next: (app) => {
            this.application.set(app);
            this.setNotesText(app.notes || '');
            // Now load the job
            if (app.jobId) {
              this.jobsApi.getById(app.jobId).subscribe({
                next: (job) => {
                  this.job.set(job);
                  this.loading.set(false);
                  this.buildActivity();
                  this.loadJobExtras(job.id);
                  this.jobsApi.getDocumentsForJob(app.jobId).subscribe({
                    next: (docs) => this.documents.set(docs),
                    error: this.notify.onError('notify.loadFailed')
                  });
                },
                error: () => this.loading.set(false)
              });
            } else {
              // Build a synthetic job from application data
              this.job.set({ id: app.id, title: app.jobTitle || this.translate.instant('jobDetails.untitled'), companyName: app.jobCompanyName, url: '' });
              this.loading.set(false);
            }
          },
          error: () => this.loading.set(false)
        });
      }
    });
  }

  /** Saved-state + first note for the job (independent of the application). */
  private loadJobExtras(jobId: string): void {
    this.jobsApi.getSaved().subscribe({
      next: saved => this.saved.set(saved.some(j => j.id === jobId)),
      error: this.notify.onError('notify.loadFailed')
    });
    this.jobsApi.getSimilar(jobId, 5).subscribe({
      next: jobs => this.similarJobs.set(jobs),
      error: this.notify.onError('notify.loadFailed')
    });
    this.notesApi.getForJob(jobId).subscribe({
      next: notes => {
        if (notes.length > 0) {
          this.note = notes[0];
          this.setNotesText(notes[0].content);
        }
      },
      error: this.notify.onError('notify.loadFailed')
    });
  }

  private setNotesText(text: string): void {
    this.notesText = text;
    this.cdr.markForCheck();
  }

  stageLabel(status: ApplicationStatus | string): string { return stageLabelKey(status); }

  stageIdx(stage: ApplicationStatus | string): number {
    return STAGE_FLOW.indexOf(stage as ApplicationStatus);
  }

  moveToStage(stage: ApplicationStatus): void {
    const application = this.application();
    if (!application) return;
    this.appsApi.updateStatus(application.id, stage).subscribe({
      next: (updated) => this.application.set(updated),
      error: this.notify.onError('notify.actionFailed')
    });
  }

  onGenerate(format: string): void {
    const job = this.job();
    if (!job) return;
    if (format === 'cv') {
      this.navigateWithApplication(appId => ['/applications', appId, 'cv']);
      return;
    }
    this.router.navigate(['/apply'], { queryParams: { jobId: job.id, format } });
  }

  openDocument(doc: GeneratedDocument): void {
    if (doc.documentType === 'CV') {
      this.navigateWithApplication(appId => ['/applications', appId, 'cv']);
    } else {
      this.navigateWithApplication(
        appId => ['/applications', appId, 'output'],
        { format: this.docTypeToFormat(doc.documentType) });
    }
  }

  /** Both CV and output screens are application-scoped; create the application first if none exists yet. */
  private navigateWithApplication(route: (appId: string) => string[], queryParams?: Record<string, string>): void {
    const application = this.application();
    if (application) {
      this.router.navigate(route(application.id), { queryParams });
      return;
    }
    const job = this.job();
    if (!job || this.openingCv()) return;
    this.openingCv.set(true);
    this.appsApi.create(job.id).subscribe({
      next: app => {
        this.application.set(app);
        this.openingCv.set(false);
        this.router.navigate(route(app.id), { queryParams });
      },
      error: () => this.openingCv.set(false)
    });
  }

  private docTypeToFormat(docType: string): string {
    const map: Record<string, string> = {
      APPLICATION_TEXT: 'app',
      COVER_LETTER: 'cl',
      UNSOLICITED_APPLICATION: 'ua',
      RECRUITER_MESSAGE: 'dm',
      FOLLOW_UP_MESSAGE: 'fu',
    };
    return map[docType] ?? 'app';
  }

  docTypeLabel(docType: string): string {
    const labels: Record<string, string> = {
      CV: 'documents.type.cv',
      APPLICATION_TEXT: 'documents.type.application',
      COVER_LETTER: 'documents.type.coverLetter',
      UNSOLICITED_APPLICATION: 'documents.type.unsolicited',
      RECRUITER_MESSAGE: 'documents.type.shortPitch',
      FOLLOW_UP_MESSAGE: 'documents.type.followUp',
    };
    return labels[docType] ?? docType;
  }

  toggleSave(): void {
    const job = this.job();
    if (!job) return;
    const wasSaved = this.saved();
    this.saved.set(!wasSaved);
    (wasSaved ? this.jobsApi.unsave(job.id) : this.jobsApi.save(job.id)).subscribe({
      error: () => this.saved.set(wasSaved)
    });
  }

  openOriginal(): void {
    const url = this.job()?.url;
    if (url) window.open(url, '_blank', 'noopener');
  }

  /** User says the posting is gone: hide it here, let the server verify the URL. */
  reportTakenDown(): void {
    const job = this.job();
    if (!job || this.reportedInactive()) return;
    this.jobsApi.reportInactive(job.id).subscribe({
      next: () => {
        this.reportedInactive.set(true);
        this.saved.set(false);
      },
      error: this.notify.onError('notify.actionFailed')
    });
  }

  saveNotes(): void {
    const job = this.job();
    if (!job) return;
    const content = this.notesText.trim();
    if (this.note) {
      if (content === this.note.content) return;
      this.notesApi.update(job.id, this.note.id, content).subscribe({
        next: note => { this.note = note; this.flashNotesStatus('jobDetails.notesSaved'); },
        error: () => this.flashNotesStatus('jobDetails.notesSaveFailed')
      });
    } else if (content) {
      this.notesApi.create(job.id, content).subscribe({
        next: note => { this.note = note; this.flashNotesStatus('jobDetails.notesSaved'); },
        error: () => this.flashNotesStatus('jobDetails.notesSaveFailed')
      });
    }
  }

  private flashNotesStatus(text: string): void {
    this.notesStatus.set(text);
    setTimeout(() => this.notesStatus.set(''), 1800);
  }

  /**
   * mailto: link for reaching out before applying. The recruiter's address is
   * known only once an application exists; otherwise the compose window opens
   * without a recipient but with subject and opener prefilled.
   */
  get contactMailto(): string {
    const job = this.job();
    const to = this.application()?.recruiterEmail ?? '';
    const subject = encodeURIComponent(this.translate.instant('jobDetails.mailto.subject', {
      role: job?.title ?? this.translate.instant('jobDetails.mailto.openRole'),
      company: job?.companyName ?? this.translate.instant('jobDetails.mailto.yourCompany'),
    }));
    const body = encodeURIComponent(this.translate.instant('jobDetails.mailto.body', { role: job?.title ?? '' }));
    return `mailto:${to}?subject=${subject}&body=${body}`;
  }

  deadlinePassed(iso: string): boolean {
    return new Date(iso).getTime() < Date.now();
  }

  ageLabel(dateStr: string): string {
    if (!dateStr) return '—';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days === 0) return this.translate.instant('time.today');
    if (days < 7) return this.translate.instant('time.daysAgo', { n: days });
    if (days < 30) return this.translate.instant('time.weeksAgo', { n: Math.floor(days / 7) });
    return this.translate.instant('time.monthsAgo', { n: Math.floor(days / 30) });
  }

  private buildActivity(): void {
    const events: { time: string; what: string; who: string; dot: string }[] = [];
    const application = this.application();
    if (application) {
      if (application.appliedAt) {
        events.push({ time: this.ageLabel(application.appliedAt), what: this.translate.instant('jobDetails.activity.applicationSent'), who: this.translate.instant('jobDetails.activity.via', { source: this.job()?.source || this.translate.instant('jobDetails.activity.platform') }), dot: 'var(--jb-info)' });
      }
      events.push({ time: this.ageLabel(application.createdAt), what: this.translate.instant('jobDetails.activity.savedToPipeline'), who: this.translate.instant('jobDetails.activity.you'), dot: 'var(--jb-text-dim)' });
    }
    this.activityEvents.set(events);
  }
}
