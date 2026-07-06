import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
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
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);

  loading = true;
  job: Job | null = null;
  application: Application | null = null;
  documents: GeneratedDocument[] = [];
  notesText = '';
  tab = signal<'jd' | 'activity' | 'notes'>('jd');
  mobileShowRail = signal(false);

  stageFlow = STAGE_FLOW;

  tabs = [
    { key: 'jd' as const, label: 'Job description' },
    { key: 'activity' as const, label: 'Activity', count: 0 },
    { key: 'notes' as const, label: 'Notes', count: 0 },
  ];

  generateActions = [
    { label: 'Application', icon: 'layers' },
    { label: 'Angled CV', icon: 'doc' },
    { label: 'Recruiter email', icon: 'mail' },
    { label: 'Follow-up', icon: 'chat' },
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

  onGenerate(_action: string): void {
    // Will navigate to the appropriate generation screen
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
