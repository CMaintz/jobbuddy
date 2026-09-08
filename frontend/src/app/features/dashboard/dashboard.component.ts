import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of, catchError } from 'rxjs';
import { DashboardApiService, DetailedMetrics, WeeklyTrend } from '../../core/api/dashboard.api';
import { RemindersApiService } from '../../core/api/reminders.api';
import { NudgesApiService, Nudge } from '../../core/api/nudges.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { Application } from '../../core/models/application.model';
import { UserPreferences } from '../../core/models/user.model';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { SparklineComponent } from '../../shared/components/sparkline/sparkline.component';
import { HeatmapComponent } from '../../shared/components/heatmap/heatmap.component';
import { FunnelComponent } from '../../shared/components/funnel/funnel.component';
import { GoalRingComponent } from '../../shared/components/goal-ring/goal-ring.component';
import { activityStreak, buildFunnelStages, buildHeatmapData, FunnelStage } from '../../shared/utils/application-insights';

const HEATMAP_WEEKS = 14;

type PeriodKey = '4w' | '3m' | '6m' | 'all';

const PERIODS: { key: PeriodKey; labelKey: string; days: number; weeks: number }[] = [
  { key: '4w', labelKey: 'dashboard.period.4w', days: 28, weeks: 4 },
  { key: '3m', labelKey: 'dashboard.period.3m', days: 91, weeks: 13 },
  { key: '6m', labelKey: 'dashboard.period.6m', days: 182, weeks: 26 },
  { key: 'all', labelKey: 'dashboard.period.all', days: Infinity, weeks: 26 },
];

/** Statuses that mean the company replied in some form. */
const RESPONDED_STATUSES = new Set([
  'RECRUITER_CONTACT', 'INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND', 'OFFER', 'REJECTED',
]);
const SENT_STATUSES = new Set([
  'APPLIED', 'RECRUITER_CONTACT', 'INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND', 'OFFER', 'REJECTED', 'ARCHIVED',
]);

const STATUS_FEED: Record<string, { what: string; color: string }> = {
  SAVED: { what: 'dashboard.status.saved', color: 'var(--jb-text-dim)' },
  PREPARING: { what: 'dashboard.status.preparing', color: 'var(--jb-accent)' },
  APPLIED: { what: 'dashboard.status.applied', color: 'var(--jb-info)' },
  RECRUITER_CONTACT: { what: 'dashboard.status.recruiterContact', color: 'var(--jb-violet)' },
  INTERVIEW: { what: 'dashboard.status.interview', color: 'var(--jb-accent)' },
  TECHNICAL_TEST: { what: 'dashboard.status.technicalTest', color: 'var(--jb-accent)' },
  FINAL_ROUND: { what: 'dashboard.status.finalRound', color: 'var(--jb-accent)' },
  OFFER: { what: 'dashboard.status.offer', color: 'var(--jb-success)' },
  REJECTED: { what: 'dashboard.status.rejected', color: 'var(--jb-danger)' },
  ARCHIVED: { what: 'dashboard.status.archived', color: 'var(--jb-text-dim)' },
};

interface QueueItem {
  time: string;
  what: string;
  detail: string;
  tag: string;
  tone: 'accent' | 'info' | 'danger' | 'neutral' | 'violet' | 'success';
  hot: boolean;
}

interface FeedItem {
  time: string;
  what: string;
  who: string;
  color: string;
}

interface SavedPreview {
  name: string;
  age: string;
  hot: boolean;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbTopbarComponent,
    StatCardComponent, SparklineComponent, HeatmapComponent, FunnelComponent, GoalRingComponent,
  ],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private api = inject(DashboardApiService);
  private remindersApi = inject(RemindersApiService);
  private nudgesApi = inject(NudgesApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);
  private translate = inject(TranslateService);

  layout = signal<'dense' | 'editorial'>('dense');
  period = signal<PeriodKey>((localStorage.getItem('jb-dash-period') as PeriodKey) || '4w');
  periods = PERIODS;

  appliedThisWeek = 0;
  weeklyGoal = 10;
  responseRate = 0;
  /** Response-rate change vs the previous period of equal length, in points. Null for all-time. */
  responseDelta: number | null = null;
  sentInPeriod = 0;
  streak = 0;
  savedCount = 0;
  overdueCount = 0;

  private allApps: Application[] = [];

  appsSpark: number[] = [];
  weekData: number[] = [];
  funnelStages: FunnelStage[] = [];
  heatmapData: number[] = [];
  heatmapWeeks = HEATMAP_WEEKS;
  savedPreview: SavedPreview[] = [];
  todayQueue: QueueItem[] = [];
  recentFeed: FeedItem[] = [];

  get remainingApps(): number {
    return Math.max(0, this.weeklyGoal - this.appliedThisWeek);
  }

  get greeting(): string {
    const hour = new Date().getHours();
    return hour < 5 ? 'dashboard.greeting.late'
      : hour < 12 ? 'dashboard.greeting.morning'
      : hour < 18 ? 'dashboard.greeting.afternoon'
      : 'dashboard.greeting.evening';
  }

  ngOnInit(): void {
    forkJoin({
      metrics: this.api.getDetailedAnalytics(),
      trend: this.api.getWeeklyTrend(),
      apps: this.appsApi.getAll(),
      saved: this.jobsApi.getSaved(),
      reminders: this.remindersApi.getOpenReminders(),
      nudges: this.nudgesApi.getNudges().pipe(catchError(() => of([] as Nudge[]))),
      prefs: this.http.get<UserPreferences>('/api/v1/users/me/preferences'),
    }).subscribe({
      next: ({ metrics, trend, apps, saved, reminders, nudges, prefs }) => {
        this.allApps = apps;
        this.applyMetrics(metrics, trend, prefs);
        this.applyPeriod();
        this.recentFeed = this.buildFeed(apps);
        this.savedCount = saved.length;
        this.savedPreview = saved.slice(0, 3).map(job => ({
          name: job.companyName ?? job.title,
          age: this.ageLabel(job.postedAt),
          hot: false,
        }));
        this.buildQueue(reminders, apps, nudges);
      },
      error: () => {} // cards keep their zero states
    });
  }

  private applyMetrics(metrics: DetailedMetrics, trend: WeeklyTrend, prefs: UserPreferences): void {
    this.appliedThisWeek = metrics.appliedThisWeek;
    this.weeklyGoal = prefs.weeklyApplicationGoal ?? 10;
    this.appsSpark = (trend.daily ?? []).map(d => d.count);
    this.weekData = this.appsSpark.slice(-7);
  }

  // ── Period-scoped stats ───────────────────────────────────────

  get periodDef() {
    return PERIODS.find(p => p.key === this.period()) ?? PERIODS[0];
  }

  setPeriod(key: PeriodKey): void {
    this.period.set(key);
    localStorage.setItem('jb-dash-period', key);
    this.applyPeriod();
  }

  private applyPeriod(): void {
    const def = this.periodDef;
    const now = Date.now();
    const from = def.days === Infinity ? 0 : now - def.days * 86400000;
    const prevFrom = def.days === Infinity ? 0 : from - def.days * 86400000;

    const inPeriod = this.allApps.filter(a => this.sentTime(a) >= from);
    this.sentInPeriod = inPeriod.filter(a => SENT_STATUSES.has(a.status)).length;
    this.responseRate = this.rateOf(inPeriod);

    if (def.days === Infinity) {
      this.responseDelta = null;
    } else {
      const previous = this.allApps.filter(a => {
        const t = this.sentTime(a);
        return t >= prevFrom && t < from;
      });
      const prevSent = previous.filter(a => SENT_STATUSES.has(a.status)).length;
      this.responseDelta = prevSent > 0 ? this.responseRate - this.rateOf(previous) : null;
    }

    this.funnelStages = buildFunnelStages(inPeriod);
    this.heatmapWeeks = def.weeks;
    this.heatmapData = buildHeatmapData(inPeriod, def.weeks);
    this.streak = activityStreak(buildHeatmapData(this.allApps, 26));
  }

  private sentTime(app: Application): number {
    return new Date(app.appliedAt ?? app.createdAt).getTime();
  }

  /** Share of sent applications that got any reply, in whole percent. */
  private rateOf(apps: Application[]): number {
    const sent = apps.filter(a => SENT_STATUSES.has(a.status));
    if (sent.length === 0) return 0;
    return Math.round((sent.filter(a => RESPONDED_STATUSES.has(a.status)).length / sent.length) * 100);
  }

  private buildFeed(apps: Application[]): FeedItem[] {
    return [...apps]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 6)
      .map(app => ({
        time: this.ageLabel(app.updatedAt),
        what: STATUS_FEED[app.status]?.what ?? app.status,
        who: `${app.jobCompanyName ?? ''}${app.jobTitle ? ' · ' + app.jobTitle : ''}` || '—',
        color: STATUS_FEED[app.status]?.color ?? 'var(--jb-text-dim)',
      }));
  }

  private buildQueue(reminders: { note: string | null; dueAt: string; applicationId: string }[], apps: Application[], nudges: Nudge[]): void {
    const appMap = new Map(apps.map(a => [a.id, a]));
    const now = Date.now();
    const endOfDay = new Date();
    endOfDay.setHours(23, 59, 59, 999);

    this.overdueCount = reminders.filter(r => new Date(r.dueAt).getTime() < now).length;

    // The backend feed is the single source for the queue: it already carries due reminders and
    // outreach follow-ups, and it is the same list the daily email sends. Building them here too
    // would give the screen and the email different answers.
    this.todayQueue = nudges.map(n => this.nudgeItem(n, appMap)).slice(0, 5);
  }

  private nudgeItem(n: Nudge, appMap?: Map<string, Application>): QueueItem {
    const app = n.applicationId ? appMap?.get(n.applicationId) : undefined;
    const detail = `${n.companyName ?? app?.jobCompanyName ?? ''}`
      + `${n.jobTitle || app?.jobTitle ? ' · ' + (n.jobTitle ?? app?.jobTitle) : ''}` || '—';
    const overdue = !!n.deadline && new Date(n.deadline).getTime() < Date.now();

    if (n.type === 'REMINDER_DUE') {
      return {
        time: overdue ? 'dashboard.queue.now' : 'dashboard.queue.today',
        what: n.note || 'dashboard.queue.followUp',
        detail,
        tag: 'dashboard.tag.followUp',
        tone: overdue ? 'danger' : 'info',
        hot: overdue,
      };
    }
    if (n.type === 'OUTREACH_FOLLOW_UP') {
      return {
        time: overdue ? 'dashboard.queue.now' : 'dashboard.queue.today',
        what: 'dashboard.queue.outreachFollowUp',
        detail: n.note ? `${n.companyName ?? ''} · ${n.note}` : (n.companyName ?? '—'),
        tag: 'dashboard.tag.outreach',
        tone: overdue ? 'danger' : 'info',
        hot: overdue,
      };
    }
    if (n.type === 'DEADLINE_SOON') {
      const daysLeft = Math.max(0, Math.ceil((new Date(n.deadline ?? '').getTime() - Date.now()) / 86400000));
      return {
        time: daysLeft === 0 ? 'dashboard.queue.today' : `${daysLeft}d`,
        what: 'dashboard.queue.applyDeadline',
        detail,
        tag: 'dashboard.tag.deadline',
        tone: daysLeft <= 2 ? 'danger' : 'info',
        hot: daysLeft <= 2,
      };
    }
    return {
      time: `${n.daysSinceApplied}d`,
      what: 'dashboard.queue.noReply',
      detail,
      tag: 'dashboard.tag.noReply',
      tone: 'violet',
      hot: false,
    };
  }

  ageLabel(iso?: string): string {
    if (!iso) return '—';
    const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 60) return this.translate.instant('time.compact.minutes', { n: Math.max(mins, 1) });
    const hours = Math.floor(mins / 60);
    if (hours < 24) return this.translate.instant('time.compact.hours', { n: hours });
    const days = Math.floor(hours / 24);
    if (days < 7) return this.translate.instant('time.compact.days', { n: days });
    return this.translate.instant('time.compact.weeks', { n: Math.floor(days / 7) });
  }
}
