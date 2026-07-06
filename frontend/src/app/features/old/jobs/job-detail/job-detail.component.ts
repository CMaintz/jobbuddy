import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { JobsApiService } from '../../../../core/api/jobs.api';
import { NotesApiService } from '../../../../core/api/notes.api';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { AiApiService } from '../../../../core/api/ai.api';
import { InterviewPrepApiService, InterviewQuestion } from '../../../../core/api/interview-prep.api';
import { RemindersApiService, FollowUpReminder } from '../../../../core/api/reminders.api';
import { Job } from '../../../../core/models/job.model';
import { Note } from '../../../../core/models/note.model';
import { GeneratedDocument } from '../../../../core/models/generated-document.model';
import { Application } from '../../../../core/models/application.model';
import { ConfirmDeleteButtonComponent } from '../../../../shared/components/ui/confirm-delete-button.component';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { clearTextAfter, runAction } from '../../../../shared/utils/async-ui';
import { LucideAngularModule, MapPin, Briefcase, Monitor, TrendingUp, Banknote } from 'lucide-angular';
import { SkillGapApiService, SkillGapResult } from '../../../../core/api/skill-gap.api';

const CATEGORY_LABELS: Record<string, string> = {
  BEHAVIORAL: 'Behavioral',
  TECHNICAL: 'Technical',
  SITUATIONAL: 'Situational',
  COMPANY: 'Company',
};

const CATEGORY_COLORS: Record<string, string> = {
  BEHAVIORAL: 'bg-blue-50 text-blue-700',
  TECHNICAL: 'bg-purple-50 text-purple-700',
  SITUATIONAL: 'bg-yellow-50 text-yellow-700',
  COMPANY: 'bg-green-50 text-green-700',
};

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, ConfirmDeleteButtonComponent, EmptyStateComponent, LucideAngularModule],
  templateUrl: './job-detail.component.html'
})
export class JobDetailComponent implements OnInit {
  readonly MapPinIcon = MapPin;
  readonly BriefcaseIcon = Briefcase;
  readonly MonitorIcon = Monitor;
  readonly TrendingUpIcon = TrendingUp;
  readonly BanknoteIcon = Banknote;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private jobsApi = inject(JobsApiService);
  private notesApi = inject(NotesApiService);
  private appsApi = inject(ApplicationsApiService);
  private aiApi = inject(AiApiService);
  private interviewPrepApi = inject(InterviewPrepApiService);
  private remindersApi = inject(RemindersApiService);
  private skillGapApi = inject(SkillGapApiService);

  job: Job | null = null;
  notes: Note[] = [];
  generatedDocs: GeneratedDocument[] = [];
  application: Application | null = null;
  loading = true;

  saved = false;
  saveMessage = '';
  expandedDoc: string | null = null;

  skillGap: SkillGapResult | null = null;
  skillGapLoading = false;

  newNoteContent = '';
  editingNoteId: string | null = null;
  editContent = '';

  // Recruiter outreach state
  recruiterName = '';
  recruiterEmail = '';
  recruiterMessageText = '';
  recruiterReplyText = '';
  generatingOutreach = false;
  savingOutreach = false;
  savingReply = false;
  outreachSaved = false;
  replySaved = false;

  // Reminder state
  reminders: FollowUpReminder[] = [];
  newReminderNote = '';
  newReminderDueAt = '';

  get overdueCount(): number {
    const now = new Date();
    return this.reminders.filter(r => !r.completed && new Date(r.dueAt) < now).length;
  }

  // Interview prep state
  interviewQuestions: InterviewQuestion[] = [];
  generatingQuestions = false;
  generateQuestionsError = '';
  editingStarId: string | null = null;
  editingStarText = '';
  newQuestionText = '';
  newQuestionCategory = 'BEHAVIORAL';

  get preparedCount(): number {
    return this.interviewQuestions.filter(q => q.practiced).length;
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    runAction({
      action$: this.jobsApi.getById(id),
      setLoading: value => this.loading = value,
      next: j => {
        this.job = j;
        this.notesApi.getForJob(j.id).subscribe(ns => this.notes = ns);
        this.jobsApi.getDocumentsForJob(j.id).subscribe(docs => this.generatedDocs = docs);
        this.interviewPrepApi.getQuestions(j.id).subscribe(qs => this.interviewQuestions = qs);
        this.appsApi.getAll().subscribe(apps => {
          const app = apps.find(a => a.jobId === j.id) ?? null;
          this.application = app;
          if (app) {
            this.recruiterName = app.recruiterName ?? '';
            this.recruiterEmail = app.recruiterEmail ?? '';
            this.recruiterMessageText = app.recruiterMessage ?? '';
            this.recruiterReplyText = app.recruiterReply ?? '';
            this.remindersApi.getForApplication(app.id).subscribe(rs => this.reminders = rs);
          }
        });
      }
    });
  }

  generateApplication(): void {
    if (!this.job) return;
    this.router.navigate(['/ai/generate'], { queryParams: { jobId: this.job.id } });
  }

  toggleSave(): void {
    if (!this.job) return;
    if (this.saved) {
      this.jobsApi.unsave(this.job.id).subscribe(() => {
        this.saved = false;
        this.saveMessage = 'Removed from saved jobs.';
        clearTextAfter(value => this.saveMessage = value, 2000);
      });
    } else {
      this.jobsApi.save(this.job.id).subscribe(() => {
        this.saved = true;
        this.saveMessage = 'Job saved!';
        clearTextAfter(value => this.saveMessage = value, 2000);
      });
    }
  }

  apply(): void {
    if (!this.job) return;
    this.router.navigate(['/apply', this.job.id]);
  }

  generateOutreachMessage(): void {
    if (!this.job) return;
    this.generatingOutreach = true;
    this.aiApi.generateDocument({
      jobId: this.job.id,
      documentType: 'RECRUITER_MESSAGE',
    }).subscribe({
      next: doc => {
        this.recruiterMessageText = doc.bodyContent ?? '';
        this.generatingOutreach = false;
      },
      error: () => { this.generatingOutreach = false; },
    });
  }

  saveOutreach(): void {
    if (!this.job) return;
    this.savingOutreach = true;
    this.outreachSaved = false;
    const save$ = this.application
      ? this.appsApi.updateRecruiterInfo(this.application.id, {
          recruiterName: this.recruiterName,
          recruiterEmail: this.recruiterEmail,
          recruiterMessage: this.recruiterMessageText,
        })
      : this.appsApi.create({
          jobId: this.job.id,
          status: 'SAVED',
        });

    save$.subscribe({
      next: app => {
        this.application = app;
        this.savingOutreach = false;
        this.outreachSaved = true;
        clearTextAfter(v => { if (v === '') this.outreachSaved = false; }, 3000);
      },
      error: () => { this.savingOutreach = false; },
    });
  }

  saveReply(): void {
    if (!this.job) return;
    this.savingReply = true;
    this.replySaved = false;

    const doSave = (appId: string) => {
      this.appsApi.updateRecruiterInfo(appId, { recruiterReply: this.recruiterReplyText }).subscribe({
        next: app => {
          this.application = app;
          this.savingReply = false;
          this.replySaved = true;
          clearTextAfter(v => { if (v === '') this.replySaved = false; }, 3000);
        },
        error: () => { this.savingReply = false; },
      });
    };

    if (this.application) {
      doSave(this.application.id);
    } else {
      this.appsApi.create({ jobId: this.job.id, status: 'SAVED' }).subscribe({
        next: app => { this.application = app; doSave(app.id); },
        error: () => { this.savingReply = false; },
      });
    }
  }

  // ── Interview Prep ────────────────────────────────────────────────────────

  generateInterviewQuestions(): void {
    if (!this.job) return;
    this.generatingQuestions = true;
    this.generateQuestionsError = '';
    const description = this.job.descriptionClean ?? '';
    this.interviewPrepApi.generateQuestions(this.job.id, description, 10).subscribe({
      next: qs => {
        this.interviewQuestions = [...this.interviewQuestions, ...qs];
        this.generatingQuestions = false;
      },
      error: () => {
        this.generateQuestionsError = 'Failed to generate questions. Please try again.';
        this.generatingQuestions = false;
      },
    });
  }

  addQuestionManually(): void {
    if (!this.job || !this.newQuestionText.trim()) return;
    this.interviewPrepApi.addQuestion(this.job.id, this.newQuestionText.trim(), this.newQuestionCategory)
      .subscribe(q => {
        this.interviewQuestions = [...this.interviewQuestions, q];
        this.newQuestionText = '';
      });
  }

  togglePracticed(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.updateQuestion(this.job.id, q.id, { practiced: !q.practiced }).subscribe(updated => {
      this.interviewQuestions = this.interviewQuestions.map(item => item.id === updated.id ? updated : item);
    });
  }

  startStarEdit(q: InterviewQuestion): void {
    this.editingStarId = q.id;
    this.editingStarText = q.starAnswer ?? '';
  }

  cancelStarEdit(): void {
    this.editingStarId = null;
    this.editingStarText = '';
  }

  saveStarAnswer(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.updateQuestion(this.job.id, q.id, { starAnswer: this.editingStarText }).subscribe(updated => {
      this.interviewQuestions = this.interviewQuestions.map(item => item.id === updated.id ? updated : item);
      this.cancelStarEdit();
    });
  }

  deleteQuestion(q: InterviewQuestion): void {
    if (!this.job) return;
    this.interviewPrepApi.deleteQuestion(this.job.id, q.id).subscribe(() => {
      this.interviewQuestions = this.interviewQuestions.filter(item => item.id !== q.id);
    });
  }

  // ── Reminders ─────────────────────────────────────────────────────────────

  addReminder(): void {
    if (!this.application || !this.newReminderDueAt) return;
    const dueAt = new Date(this.newReminderDueAt).toISOString();
    this.remindersApi.create(this.application.id, this.newReminderNote.trim() || 'Follow up', dueAt)
      .subscribe(r => {
        this.reminders = [...this.reminders, r].sort(
          (a, b) => new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime());
        this.newReminderNote = '';
        this.newReminderDueAt = '';
      });
  }

  setReminderIn(days: number): void {
    const d = new Date();
    d.setDate(d.getDate() + days);
    d.setHours(9, 0, 0, 0);
    this.newReminderDueAt = d.toISOString().slice(0, 16);
  }

  completeReminder(r: FollowUpReminder): void {
    if (r.completed) return;
    this.remindersApi.complete(r.id).subscribe(updated => {
      this.reminders = this.reminders.map(item => item.id === updated.id ? updated : item);
    });
  }

  deleteReminder(r: FollowUpReminder): void {
    this.remindersApi.delete(r.id).subscribe(() => {
      this.reminders = this.reminders.filter(item => item.id !== r.id);
    });
  }

  reminderRowClass(r: FollowUpReminder): string {
    if (r.completed) return 'border-gray-100 bg-gray-50 opacity-60';
    const now = new Date();
    const due = new Date(r.dueAt);
    if (due < now) return 'border-red-200 bg-red-50';
    const tomorrow = new Date(now);
    tomorrow.setDate(tomorrow.getDate() + 1);
    if (due <= tomorrow) return 'border-yellow-200 bg-yellow-50';
    return 'border-green-100 bg-green-50';
  }

  reminderDateClass(r: FollowUpReminder): string {
    if (r.completed) return 'text-gray-400';
    const now = new Date();
    if (new Date(r.dueAt) < now) return 'text-red-600 font-medium';
    return 'text-gray-500';
  }

  reminderDueLabel(r: FollowUpReminder): string {
    const due = new Date(r.dueAt);
    const now = new Date();
    const diffMs = due.getTime() - now.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays < 0) return `Overdue by ${Math.abs(diffDays)} day${Math.abs(diffDays) !== 1 ? 's' : ''}`;
    if (diffDays === 0) return 'Due today';
    if (diffDays === 1) return 'Due tomorrow';
    return `Due in ${diffDays} days`;
  }

  categoryLabel(cat: string): string {
    return CATEGORY_LABELS[cat] ?? cat;
  }

  categoryColor(cat: string): string {
    return CATEGORY_COLORS[cat] ?? 'bg-gray-100 text-gray-600';
  }

  // ── Notes ─────────────────────────────────────────────────────────────────

  addNote(): void {
    if (!this.job || !this.newNoteContent.trim()) return;
    this.notesApi.create(this.job.id, this.newNoteContent.trim()).subscribe(note => {
      this.notes = [...this.notes, note];
      this.newNoteContent = '';
    });
  }

  startEdit(note: Note): void {
    this.editingNoteId = note.id;
    this.editContent = note.content;
  }

  cancelEdit(): void {
    this.editingNoteId = null;
    this.editContent = '';
  }

  saveEdit(note: Note): void {
    if (!this.job || !this.editContent.trim()) return;
    this.notesApi.update(this.job.id, note.id, this.editContent.trim()).subscribe(updated => {
      this.notes = this.notes.map(n => n.id === updated.id ? updated : n);
      this.cancelEdit();
    });
  }

  deleteNote(noteId: string): void {
    if (!this.job) return;
    this.notesApi.delete(this.job.id, noteId).subscribe(() => {
      this.notes = this.notes.filter(n => n.id !== noteId);
    });
  }

  // ── Skill Gap ─────────────────────────────────────────────────────────────

  analyzeSkillGap(): void {
    if (!this.job) return;
    this.skillGapLoading = true;
    this.skillGapApi.analyze(this.job.id).subscribe({
      next: result => { this.skillGap = result; this.skillGapLoading = false; },
      error: () => this.skillGapLoading = false,
    });
  }
}
