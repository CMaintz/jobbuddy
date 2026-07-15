import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { GoalRingComponent } from '../../shared/components/goal-ring/goal-ring.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { AiApiService, AnalysisDimensions, AnalysisResponse } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';

interface JobOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-cv-analysis',
  standalone: true,
  imports: [CommonModule, JbTopbarComponent, FormsModule, RouterLink, JbIconComponent, JbButtonComponent, JbToastComponent, GoalRingComponent, JbPillComponent],
  templateUrl: './cv-analysis.component.html'
})
export class CvAnalysisComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);

  toast = signal('');
  analyzing = signal(false);
  result = signal<AnalysisResponse | null>(null);

  /** 'general' judges the master CV on its own; a job id or pasted JD makes it job-aware. */
  target = signal<'general' | 'job' | 'paste'>('general');
  selectedJobId = '';
  pastedJd = '';
  jobOptions: JobOption[] = [];

  targets = [
    { key: 'general' as const, label: 'General strength', hint: 'Clarity, achievements, ATS readiness' },
    { key: 'job' as const, label: 'Against a job', hint: 'Pick from saved roles & applications' },
    { key: 'paste' as const, label: 'Against a pasted JD', hint: 'Paste any job description' },
  ];

  ngOnInit(): void {
    // Saved roles + pipeline jobs feed the target picker
    forkJoin({ saved: this.jobsApi.getSaved(), apps: this.appsApi.getAll() }).subscribe({
      next: ({ saved, apps }) => {
        const seen = new Set<string>();
        const options: JobOption[] = [];
        for (const job of saved) {
          if (seen.has(job.id)) continue;
          seen.add(job.id);
          options.push({ id: job.id, label: `${job.companyName ?? '?'} — ${job.title}` });
        }
        for (const app of apps) {
          if (!app.jobId || seen.has(app.jobId)) continue;
          seen.add(app.jobId);
          options.push({ id: app.jobId, label: `${app.jobCompanyName ?? '?'} — ${app.jobTitle ?? ''}` });
        }
        this.jobOptions = options;
        if (options.length > 0) this.selectedJobId = options[0].id;
      },
      error: () => {} // picker just stays empty
    });
  }

  canAnalyze(): boolean {
    if (this.analyzing()) return false;
    if (this.target() === 'job') return !!this.selectedJobId;
    if (this.target() === 'paste') return this.pastedJd.trim().length > 40;
    return true;
  }

  analyze(): void {
    if (!this.canAnalyze()) return;
    this.analyzing.set(true);
    this.result.set(null);
    this.aiApi.analyze({
      jobId: this.target() === 'job' ? this.selectedJobId : undefined,
      jobDescription: this.target() === 'paste' ? this.pastedJd.trim() : undefined,
    }).subscribe({
      next: res => {
        this.analyzing.set(false);
        this.result.set(res);
      },
      error: () => {
        this.analyzing.set(false);
        this.toast.set('Analysis failed — make sure your master CV has content, then try again');
      }
    });
  }

  scoreColor(score: number): string {
    return score >= 75 ? 'var(--jb-success)' : score >= 50 ? 'var(--jb-accent-2)' : 'var(--jb-danger)';
  }

  /** Weights match the server-side overall: 30/25/15/30; location is a veto, shown separately. */
  dimensionRows(d: AnalysisDimensions): { label: string; value: number }[] {
    return [
      { label: 'Technical skills', value: d.technicalSkills ?? 0 },
      { label: 'Experience', value: d.experience ?? 0 },
      { label: 'Culture fit', value: d.cultureFit ?? 0 },
      { label: 'Career alignment', value: d.careerAlignment ?? 0 },
    ];
  }
}
