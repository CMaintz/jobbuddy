import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { GoalRingComponent } from '../../shared/components/goal-ring/goal-ring.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { AiApiService, AnalysisDimensions, AnalysisResponse, SkillGapReport } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';

interface JobOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-cv-analysis',
  standalone: true,
  imports: [CommonModule, JbTopbarComponent, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent, JbToastComponent, GoalRingComponent, JbPillComponent],
  templateUrl: './cv-analysis.component.html'
})
export class CvAnalysisComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);
  private translate = inject(TranslateService);

  toast = signal('');
  analyzing = signal(false);
  result = signal<AnalysisResponse | null>(null);
  gapReport = signal<SkillGapReport | null>(null);

  /** 'general' judges the master CV on its own; a job id or pasted JD makes it job-aware. */
  target = signal<'general' | 'job' | 'paste' | 'pipeline'>('general');
  selectedJobId = '';
  pastedJd = '';
  jobOptions: JobOption[] = [];

  targets = [
    { key: 'general' as const, label: 'analysis.target.general.label', hint: 'analysis.target.general.hint' },
    { key: 'job' as const, label: 'analysis.target.job.label', hint: 'analysis.target.job.hint' },
    { key: 'paste' as const, label: 'analysis.target.paste.label', hint: 'analysis.target.paste.hint' },
    { key: 'pipeline' as const, label: 'analysis.target.pipeline.label', hint: 'analysis.target.pipeline.hint' },
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
    this.gapReport.set(null);

    if (this.target() === 'pipeline') {
      this.aiApi.analyzeSkillGaps().subscribe({
        next: report => {
          this.analyzing.set(false);
          this.gapReport.set(report);
        },
        error: () => {
          this.analyzing.set(false);
          this.toast.set(this.translate.instant('analysis.toast.gapFailed'));
        }
      });
      return;
    }

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
        this.toast.set(this.translate.instant('analysis.toast.analyzeFailed'));
      }
    });
  }

  scoreColor(score: number): string {
    return score >= 75 ? 'var(--jb-success)' : score >= 50 ? 'var(--jb-accent-2)' : 'var(--jb-danger)';
  }

  gapTone(priority: string): 'danger' | 'accent' | 'neutral' {
    return priority === 'HIGH' ? 'danger' : priority === 'MEDIUM' ? 'accent' : 'neutral';
  }

  /** Bar length relative to the most-demanded gap in the report. */
  gapWidth(report: SkillGapReport, gap: { demand: number }): number {
    const max = Math.max(...report.gaps.map(g => g.demand), 1);
    return Math.round((gap.demand / max) * 100);
  }

  /** Weights match the server-side overall: 30/25/15/30; location is a veto, shown separately. */
  dimensionRows(d: AnalysisDimensions): { label: string; value: number }[] {
    return [
      { label: 'analysis.dim.technical', value: d.technicalSkills ?? 0 },
      { label: 'analysis.dim.experience', value: d.experience ?? 0 },
      { label: 'analysis.dim.culture', value: d.cultureFit ?? 0 },
      { label: 'analysis.dim.career', value: d.careerAlignment ?? 0 },
    ];
  }
}
