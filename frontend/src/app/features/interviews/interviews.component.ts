import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { InterviewPrepApiService, InterviewQuestion } from '../../core/api/interview-prep.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';

const INTERVIEW_STAGES: ApplicationStatus[] = ['RECRUITER_CONTACT', 'INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND'];

const STAGE_LABELS: Record<string, string> = {
  RECRUITER_CONTACT: 'Screen', INTERVIEW: 'Interview',
  TECHNICAL_TEST: 'Technical', FINAL_ROUND: 'Final round',
};

const CATEGORY_TONES: Record<string, 'accent' | 'info' | 'violet' | 'neutral'> = {
  BEHAVIORAL: 'info', TECHNICAL: 'accent', SITUATIONAL: 'violet', COMPANY: 'neutral',
};

@Component({
  selector: 'app-interviews',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, CompanyMarkComponent],
  templateUrl: './interviews.component.html'
})
export class InterviewsComponent implements OnInit {
  private appsApi = inject(ApplicationsApiService);
  private prepApi = inject(InterviewPrepApiService);
  private jobsApi = inject(JobsApiService);
  private router = inject(Router);

  loading = signal(true);
  toast = signal('');
  interviews = signal<Application[]>([]);
  selected = signal<Application | null>(null);
  questions = signal<InterviewQuestion[]>([]);
  loadingQuestions = signal(false);
  generating = signal(false);
  expandedQuestion = signal<string | null>(null);

  stageLabel = (s: string) => STAGE_LABELS[s] ?? s;
  categoryTone = (c: string) => CATEGORY_TONES[c] ?? 'neutral';

  ngOnInit(): void {
    this.appsApi.getAll().subscribe({
      next: apps => {
        const inInterview = apps.filter(a => INTERVIEW_STAGES.includes(a.status));
        this.interviews.set(inInterview);
        this.loading.set(false);
        if (inInterview.length > 0) this.select(inInterview[0]);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Could not load applications');
      }
    });
  }

  select(app: Application): void {
    this.selected.set(app);
    this.questions.set([]);
    this.loadingQuestions.set(true);
    this.prepApi.getQuestions(app.jobId).subscribe({
      next: qs => {
        this.questions.set(qs);
        this.loadingQuestions.set(false);
      },
      error: () => this.loadingQuestions.set(false)
    });
  }

  generate(): void {
    const app = this.selected();
    if (!app || this.generating()) return;
    this.generating.set(true);
    // Prep generation needs the JD — fetch the job first
    this.jobsApi.getById(app.jobId).subscribe({
      next: job => {
        this.prepApi.generateQuestions(app.jobId, job.descriptionClean ?? job.title, 10).subscribe({
          next: qs => {
            this.questions.set(qs);
            this.generating.set(false);
          },
          error: () => {
            this.generating.set(false);
            this.toast.set('Question generation failed — try again');
          }
        });
      },
      error: () => {
        this.generating.set(false);
        this.toast.set('Could not load the job description');
      }
    });
  }

  togglePracticed(q: InterviewQuestion): void {
    const app = this.selected();
    if (!app) return;
    const practiced = !q.practiced;
    this.questions.update(qs => qs.map(x => x.id === q.id ? { ...x, practiced } : x));
    this.prepApi.updateQuestion(app.jobId, q.id, { practiced }).subscribe({
      error: () => this.questions.update(qs => qs.map(x => x.id === q.id ? { ...x, practiced: !practiced } : x))
    });
  }

  saveAnswer(q: InterviewQuestion, answer: string): void {
    const app = this.selected();
    if (!app || answer === (q.starAnswer ?? '')) return;
    this.prepApi.updateQuestion(app.jobId, q.id, { starAnswer: answer }).subscribe({
      next: () => this.questions.update(qs => qs.map(x => x.id === q.id ? { ...x, starAnswer: answer } : x)),
      error: () => this.toast.set('Could not save the answer')
    });
  }

  practicedCount(): number {
    return this.questions().filter(q => q.practiced).length;
  }

  openApplication(app: Application): void {
    this.router.navigate(['/applications', app.id]);
  }
}
