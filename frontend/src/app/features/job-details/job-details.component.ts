import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { NotesApiService } from '../../core/api/notes.api';
import { Note } from '../../core/models/note.model';
import { Job } from '../../core/models/job.model';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { GeneratedDocument } from '../../core/models/generated-document.model';

const STAGE_FLOW: ApplicationStatus[] = ['SAVED', 'APPLIED', 'RECRUITER_CONTACT', 'INTERVIEW', 'OFFER'];

const STAGE_LABELS: Record<string, string> = {
  SAVED: 'Saved', PREPARING: 'Preparing', APPLIED: 'Applied',
  RECRUITER_CONTACT: 'Screen', INTERVIEW: 'Interview', TECHNICAL_TEST: 'Technical',
  FINAL_ROUND: 'Final', OFFER: 'Offer', REJECTED: 'Rejected', ARCHIVED: 'Archived'
};

const STAGE_TONES: Record<string, string> = {
  SAVED: 'neutral', PREPARING: 'neutral', APPLIED: 'info',
  RECRUITER_CONTACT: 'violet', INTERVIEW: 'accent', TECHNICAL_TEST: 'accent',
  FINAL_ROUND: 'accent', OFFER: 'success', REJECTED: 'danger', ARCHIVED: 'neutral'
};

@Component({
  selector: 'app-job-details',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent, FitBarComponent],
  templateUrl: './job-details.component.html'
})
export class JobDetailsComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);
  private notesApi = inject(NotesApiService);

  loading = true;
  job: Job | null = null;
  application: Application | null = null;
  documents: GeneratedDocument[] = [];
  notesText = '';
  notesStatus = signal('');
  saved = signal(false);
  private note: Note | null = null;
  tab = signal<'jd' | 'activity' | 'notes'>('jd');
  mobileShowRail = signal(false);

  stageFlow = STAGE_FLOW;

  tabs = [
    { key: 'jd' as const, label: 'Job description' },
    { key: 'activity' as const, label: 'Activity', count: 0 },
    { key: 'notes' as const, label: 'Notes', count: 0 },
  ];

  generateActions = [
    { label: 'Application', icon: 'layers', format: 'application' },
    { label: 'Cover letter', icon: 'doc', format: 'cover-letter' },
    { label: 'Short pitch', icon: 'mail', format: 'short-pitch' },
  ];

  activityEvents: { time: string; what: string; who: string; dot: string }[] = [];

  get salaryRange(): string {
    if (!this.job?.salaryMin && !this.job?.salaryMax) return '';
    const cur = this.job.currency || '';
    if (this.job.salaryMin && this.job.salaryMax) return `${cur}${this.job.salaryMin}–${this.job.salaryMax}`;
    if (this.job.salaryMin) return `${cur}${this.job.salaryMin}+`;
    return `Up to ${cur}${this.job!.salaryMax}`;
  }

  get keywords(): string[] {
    return [
      ...(this.job?.technologies || []),
      ...(this.job?.skills || []),
      ...(this.job?.aiTags || []),
    ].slice(0, 12);
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.loading = false; return; }

    // Try loading as a job first
    this.jobsApi.getById(id).subscribe({
      next: (job) => {
        this.job = job;
        this.loading = false;
        this.buildActivity();
        this.loadJobExtras(job.id);
        // Try to load documents for this job
        this.jobsApi.getDocumentsForJob(id).subscribe({
          next: (docs) => { this.documents = docs; this.tabs[1].count = this.activityEvents.length; },
          error: () => {}
        });
      },
      error: () => {
        // Maybe the ID is an application ID — try loading application
        this.appsApi.getById(id).subscribe({
          next: (app) => {
            this.application = app;
            this.notesText = app.notes || '';
            // Now load the job
            if (app.jobId) {
              this.jobsApi.getById(app.jobId).subscribe({
                next: (job) => {
                  this.job = job;
                  this.loading = false;
                  this.buildActivity();
                  this.loadJobExtras(job.id);
                  this.jobsApi.getDocumentsForJob(app.jobId).subscribe({
                    next: (docs) => this.documents = docs,
                    error: () => {}
                  });
                },
                error: () => this.loading = false
              });
            } else {
              // Build a synthetic job from application data
              this.job = { id: app.id, title: app.jobTitle || 'Untitled', companyName: app.jobCompanyName, url: '' };
              this.loading = false;
            }
          },
          error: () => this.loading = false
        });
      }
    });
  }

  /** Saved-state + first note for the job (independent of the application). */
  private loadJobExtras(jobId: string): void {
    this.jobsApi.getSaved().subscribe({
      next: saved => this.saved.set(saved.some(j => j.id === jobId)),
      error: () => {}
    });
    this.notesApi.getForJob(jobId).subscribe({
      next: notes => {
        if (notes.length > 0) {
          this.note = notes[0];
          this.notesText = notes[0].content;
        }
      },
      error: () => {}
    });
  }

  stageLabel(status: ApplicationStatus | string): string {
    return STAGE_LABELS[status] ?? status;
  }

  stageTone(status: ApplicationStatus | string): 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet' {
    return (STAGE_TONES[status] as any) ?? 'neutral';
  }

  stageIdx(stage: ApplicationStatus | string): number {
    return STAGE_FLOW.indexOf(stage as ApplicationStatus);
  }

  moveToStage(stage: ApplicationStatus): void {
    if (!this.application) return;
    this.appsApi.updateStatus(this.application.id, stage).subscribe({
      next: (updated) => { this.application = updated; },
      error: () => {}
    });
  }

  onGenerate(format: string): void {
    if (!this.job) return;
    this.router.navigate(['/apply'], { queryParams: { jobId: this.job.id, format } });
  }

  toggleSave(): void {
    if (!this.job) return;
    const wasSaved = this.saved();
    this.saved.set(!wasSaved);
    (wasSaved ? this.jobsApi.unsave(this.job.id) : this.jobsApi.save(this.job.id)).subscribe({
      error: () => this.saved.set(wasSaved)
    });
  }

  openOriginal(): void {
    if (this.job?.url) window.open(this.job.url, '_blank', 'noopener');
  }

  saveNotes(): void {
    if (!this.job) return;
    const content = this.notesText.trim();
    if (this.note) {
      if (content === this.note.content) return;
      this.notesApi.update(this.job.id, this.note.id, content).subscribe({
        next: note => { this.note = note; this.flashNotesStatus('saved'); },
        error: () => this.flashNotesStatus('save failed')
      });
    } else if (content) {
      this.notesApi.create(this.job.id, content).subscribe({
        next: note => { this.note = note; this.flashNotesStatus('saved'); },
        error: () => this.flashNotesStatus('save failed')
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
    const to = this.application?.recruiterEmail ?? '';
    const subject = encodeURIComponent(`Question about the ${this.job?.title ?? 'open'} role at ${this.job?.companyName ?? 'your company'}`);
    const body = encodeURIComponent(
      `Hi,\n\nI came across the ${this.job?.title ?? ''} opening and have a quick question before applying.\n\n`);
    return `mailto:${to}?subject=${subject}&body=${body}`;
  }

  ageLabel(dateStr: string): string {
    if (!dateStr) return '—';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days === 0) return 'today';
    if (days === 1) return '1d ago';
    if (days < 7) return `${days}d ago`;
    if (days < 30) return `${Math.floor(days / 7)}w ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }

  private buildActivity(): void {
    const events: { time: string; what: string; who: string; dot: string }[] = [];
    if (this.application) {
      if (this.application.appliedAt) {
        events.push({ time: this.ageLabel(this.application.appliedAt), what: 'Application sent', who: 'Via ' + (this.job?.source || 'platform'), dot: 'var(--jb-info)' });
      }
      events.push({ time: this.ageLabel(this.application.createdAt), what: 'Saved to pipeline', who: 'You', dot: 'var(--jb-text-dim)' });
    }
    this.activityEvents = events;
    this.tabs[1].count = events.length;
  }
}
