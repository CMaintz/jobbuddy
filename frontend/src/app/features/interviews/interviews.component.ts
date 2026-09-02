import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { stageLabelKey } from '../../shared/components/status-chip/status-chip.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { InterviewPrepApiService, InterviewQuestion, MockInterviewTurn } from '../../core/api/interview-prep.api';
import { RemindersApiService } from '../../core/api/reminders.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';

const INTERVIEW_STAGES: ApplicationStatus[] = ['RECRUITER_CONTACT', 'INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND'];

const CATEGORY_TONES: Record<string, 'accent' | 'info' | 'violet' | 'neutral'> = {
  BEHAVIORAL: 'info', TECHNICAL: 'accent', SITUATIONAL: 'violet', COMPANY: 'neutral',
};

@Component({
  selector: 'app-interviews',
  standalone: true,
  imports: [CommonModule, JbTopbarComponent, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, CompanyMarkComponent, JbModalComponent],
  templateUrl: './interviews.component.html'
})
export class InterviewsComponent implements OnInit {
  private appsApi = inject(ApplicationsApiService);
  private prepApi = inject(InterviewPrepApiService);
  private remindersApi = inject(RemindersApiService);
  private router = inject(Router);
  private translate = inject(TranslateService);

  loading = signal(true);
  toast = signal('');
  interviews = signal<Application[]>([]);
  selected = signal<Application | null>(null);
  questions = signal<InterviewQuestion[]>([]);
  loadingQuestions = signal(false);
  /** Prep-pack extras — transient, refreshed on each generation. */
  consistencyBrief = signal<string[]>([]);
  questionsToAsk = signal<string[]>([]);
  generating = signal(false);
  expandedQuestion = signal<string | null>(null);

  // Log-interview modal
  logOpen = signal(false);
  logSaving = signal(false);
  allApps: Application[] = [];
  logAppId = '';
  logStage: ApplicationStatus = 'INTERVIEW';
  logWhen = '';
  logNote = '';
  logStages: { key: ApplicationStatus; label: string }[] = [
    { key: 'RECRUITER_CONTACT', label: 'interviews.logStage.screen' },
    { key: 'INTERVIEW', label: 'interviews.logStage.interview' },
    { key: 'TECHNICAL_TEST', label: 'interviews.logStage.technical' },
    { key: 'FINAL_ROUND', label: 'interviews.logStage.finalRound' },
  ];

  exportingIcs = signal(false);

  // Mock interview roleplay — stateless: this component owns the transcript
  roleplayOpen = signal(false);
  roleplayTurns = signal<MockInterviewTurn[]>([]);
  roleplayBusy = signal(false);
  /** Coaching feedback shown after "End & get feedback" — replaces the composer. */
  roleplayFeedback = signal('');
  roleplayInput = '';

  stageLabel = (s: string) => stageLabelKey(s);
  categoryTone = (c: string) => CATEGORY_TONES[c] ?? 'neutral';

  ngOnInit(): void {
    this.appsApi.getAll().subscribe({
      next: apps => {
        this.allApps = apps.filter(a => !['REJECTED', 'ARCHIVED'].includes(a.status));
        const inInterview = apps.filter(a => INTERVIEW_STAGES.includes(a.status));
        this.interviews.set(inInterview);
        this.loading.set(false);
        if (inInterview.length > 0) this.select(inInterview[0]);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set(this.translate.instant('interviews.toast.loadFailed'));
      }
    });
  }

  // ── Log interview ─────────────────────────────────────────────

  openLog(): void {
    this.logAppId = this.selected()?.id ?? this.allApps[0]?.id ?? '';
    this.logStage = 'INTERVIEW';
    const inTwoDays = new Date(Date.now() + 2 * 86400000);
    inTwoDays.setMinutes(0, 0, 0);
    this.logWhen = toLocalDatetimeInput(inTwoDays);
    this.logNote = '';
    this.logOpen.set(true);
  }

  saveLog(): void {
    if (!this.logAppId || !this.logWhen || this.logSaving()) return;
    this.logSaving.set(true);
    const dueAt = new Date(this.logWhen).toISOString();
    const app = this.allApps.find(a => a.id === this.logAppId);
    const stageLabel = this.translate.instant(this.logStages.find(s => s.key === this.logStage)?.label ?? 'interviews.logStage.interview');
    const note = `${this.translate.instant('interviews.reminderPrefix')} ${stageLabel}`
      + (this.logNote.trim() ? `: ${this.logNote.trim()}` : '');

    this.appsApi.updateStatus(this.logAppId, this.logStage).subscribe({
      next: updated => {
        this.remindersApi.create(this.logAppId, note, dueAt).subscribe({
          next: () => {
            this.logSaving.set(false);
            this.logOpen.set(false);
            this.toast.set(this.translate.instant('interviews.toast.logged', { company: app?.jobCompanyName ?? this.translate.instant('interviews.theRole') }));
            this.refreshInterviews(updated);
          },
          error: () => {
            this.logSaving.set(false);
            this.toast.set(this.translate.instant('interviews.toast.reminderFailed'));
            this.logOpen.set(false);
            this.refreshInterviews(updated);
          }
        });
      },
      error: () => {
        this.logSaving.set(false);
        this.toast.set(this.translate.instant('interviews.toast.stageFailed'));
      }
    });
  }

  private refreshInterviews(updated: Application): void {
    this.allApps = this.allApps.map(a => a.id === updated.id ? updated : a);
    const inInterview = this.allApps.filter(a => INTERVIEW_STAGES.includes(a.status));
    this.interviews.set(inInterview);
    if (!this.selected() || this.selected()!.id === updated.id) {
      const match = inInterview.find(a => a.id === updated.id);
      if (match) this.select(match);
    }
  }

  // ── Calendar export ───────────────────────────────────────────

  /** Downloads upcoming reminders (interviews & follow-ups) as an .ics file. */
  exportCalendar(): void {
    if (this.exportingIcs()) return;
    this.exportingIcs.set(true);
    this.remindersApi.getOpenReminders().subscribe({
      next: reminders => {
        this.exportingIcs.set(false);
        const upcoming = reminders.filter(r => new Date(r.dueAt).getTime() > Date.now() - 86400000);
        if (upcoming.length === 0) {
          this.toast.set(this.translate.instant('interviews.toast.noExport'));
          return;
        }
        const appById = new Map(this.allApps.map(a => [a.id, a]));
        const events = upcoming.map(r => {
          const app = appById.get(r.applicationId);
          const where = app ? `${app.jobCompanyName ?? ''} — ${app.jobTitle ?? ''}` : '';
          return icsEvent(r.id, new Date(r.dueAt), r.note || this.translate.instant('interviews.followUpFallback'), where);
        });
        const ics = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Jobbuddy//EN', ...events, 'END:VCALENDAR'].join('\r\n');
        const blob = new Blob([ics], { type: 'text/calendar;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'jobbuddy-interviews.ics';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.exportingIcs.set(false);
        this.toast.set(this.translate.instant('interviews.toast.exportLoadFailed'));
      }
    });
  }

  select(app: Application): void {
    this.selected.set(app);
    this.questions.set([]);
    this.consistencyBrief.set([]);
    this.questionsToAsk.set([]);
    this.loadingQuestions.set(true);
    this.prepApi.getQuestions(app.jobId).subscribe({
      next: qs => {
        this.questions.set(qs);
        this.loadingQuestions.set(false);
      },
      error: () => this.loadingQuestions.set(false)
    });
  }

  /**
   * Full prep pack: gap-targeted questions (persisted, appended to the list),
   * a consistency brief from the submitted documents, and questions to ask.
   */
  generate(): void {
    const app = this.selected();
    if (!app || this.generating()) return;
    this.generating.set(true);
    this.prepApi.generatePrepPack(app.jobId).subscribe({
      next: pack => {
        this.questions.update(qs => [...qs, ...pack.questions]);
        this.consistencyBrief.set(pack.consistencyBrief ?? []);
        this.questionsToAsk.set(pack.questionsToAsk ?? []);
        this.generating.set(false);
      },
      error: () => {
        this.generating.set(false);
        this.toast.set(this.translate.instant('interviews.toast.prepFailed'));
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
      error: () => this.toast.set(this.translate.instant('interviews.toast.answerFailed'))
    });
  }

  practicedCount(): number {
    return this.questions().filter(q => q.practiced).length;
  }

  // ── Mock interview roleplay ───────────────────────────────────

  openRoleplay(): void {
    const app = this.selected();
    if (!app) return;
    this.roleplayTurns.set([]);
    this.roleplayFeedback.set('');
    this.roleplayInput = '';
    this.roleplayOpen.set(true);
    this.roleplayBusy.set(true);
    this.prepApi.roleplay(app.jobId, []).subscribe({
      next: res => {
        this.roleplayTurns.set([{ role: 'interviewer', content: res.reply }]);
        this.roleplayBusy.set(false);
      },
      error: () => {
        this.roleplayBusy.set(false);
        this.roleplayOpen.set(false);
        this.toast.set(this.translate.instant('interviews.toast.mockFailed'));
      }
    });
  }

  sendRoleplayAnswer(): void {
    const app = this.selected();
    const answer = this.roleplayInput.trim();
    if (!app || !answer || this.roleplayBusy()) return;
    this.roleplayInput = '';
    this.roleplayTurns.update(t => [...t, { role: 'candidate', content: answer }]);
    this.roleplayBusy.set(true);
    this.prepApi.roleplay(app.jobId, this.roleplayTurns()).subscribe({
      next: res => {
        this.roleplayTurns.update(t => [...t, { role: 'interviewer', content: res.reply }]);
        this.roleplayBusy.set(false);
      },
      error: () => {
        this.roleplayBusy.set(false);
        this.toast.set(this.translate.instant('interviews.toast.noResponse'));
      }
    });
  }

  endRoleplay(): void {
    const app = this.selected();
    if (!app || this.roleplayBusy()) return;
    const transcript = this.roleplayInput.trim()
      ? [...this.roleplayTurns(), { role: 'candidate' as const, content: this.roleplayInput.trim() }]
      : this.roleplayTurns();
    this.roleplayInput = '';
    this.roleplayTurns.set(transcript);
    this.roleplayBusy.set(true);
    this.prepApi.roleplay(app.jobId, transcript, true).subscribe({
      next: res => {
        this.roleplayFeedback.set(res.reply);
        this.roleplayBusy.set(false);
      },
      error: () => {
        this.roleplayBusy.set(false);
        this.toast.set(this.translate.instant('interviews.toast.feedbackFailed'));
      }
    });
  }

  openApplication(app: Application): void {
    this.router.navigate(['/applications', app.id]);
  }
}

/** Formats a Date for <input type="datetime-local"> (local time, no seconds). */
function toLocalDatetimeInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function icsEvent(uid: string, start: Date, summary: string, description: string): string {
  const fmt = (d: Date) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
  const end = new Date(start.getTime() + 3600000); // 1h default duration
  return [
    'BEGIN:VEVENT',
    `UID:${uid}@jobbuddy`,
    `DTSTAMP:${fmt(new Date())}`,
    `DTSTART:${fmt(start)}`,
    `DTEND:${fmt(end)}`,
    `SUMMARY:${esc(summary)}`,
    description ? `DESCRIPTION:${esc(description)}` : '',
    'END:VEVENT',
  ].filter(Boolean).join('\r\n');
}
