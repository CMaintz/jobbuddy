import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardApiService, DashboardData } from '../../core/api/dashboard.api';
import { RemindersApiService, FollowUpReminder } from '../../core/api/reminders.api';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { SparklineComponent } from '../../shared/components/sparkline/sparkline.component';
import { HeatmapComponent } from '../../shared/components/heatmap/heatmap.component';
import { FunnelComponent } from '../../shared/components/funnel/funnel.component';
import { GoalRingComponent } from '../../shared/components/goal-ring/goal-ring.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterLink, JbIconComponent, JbButtonComponent, JbPillComponent,
    StatCardComponent, SparklineComponent, HeatmapComponent, FunnelComponent, GoalRingComponent,
  ],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit {
  private api = inject(DashboardApiService);
  private remindersApi = inject(RemindersApiService);

  data: DashboardData | null = null;
  layout = signal<'dense' | 'editorial'>('dense');

  appliedThisWeek = 7;
  remainingApps = 3;
  overdueCount = 1;

  // Sparkline data
  appsSpark = [2, 4, 3, 5, 7, 6, 8, 5, 9, 7, 10, 8, 11, 12];
  respSpark = [10, 14, 12, 18, 15, 20, 19, 22, 21, 23];
  weekData = [3, 2, 4, 1, 5, 0, 2];

  savedPreview = [
    { name: 'Anthropic', age: '2d', hot: true },
    { name: 'Vercel', age: '5d', hot: false },
    { name: 'Linear', age: '1w', hot: false },
  ];

  todayQueue = [
    { time: '09:30', what: 'Interview prep', detail: 'Frontend Eng · 2nd round', tag: 'interview', tone: 'accent' as const, hot: false },
    { time: '11:00', what: 'Follow up', detail: 'Last reply: 3 days ago', tag: 'follow-up', tone: 'info' as const, hot: false },
    { time: '14:00', what: 'Polish CV for role', detail: 'AI · Design Engineer', tag: 'draft', tone: 'violet' as const, hot: false },
    { time: 'now', what: 'Reply to recruiter', detail: 'Senior Frontend', tag: 'reply', tone: 'danger' as const, hot: true },
  ];

  recentFeed = [
    { time: '14m', what: 'Cover letter generated', who: 'Design Engineer', color: 'var(--jb-accent)' },
    { time: '2h', what: 'Applied via Greenhouse', who: 'Product Engineer', color: 'var(--jb-info)' },
    { time: '3h', what: 'CV angled for role', who: 'Senior Frontend', color: 'var(--jb-violet)' },
    { time: '6h', what: 'Saved from extension', who: 'Web Eng', color: 'var(--jb-text-dim)' },
    { time: '1d', what: 'Recruiter replied', who: 'Frontend Eng', color: 'var(--jb-success)' },
    { time: '1d', what: 'Rejected', who: 'Product Eng', color: 'var(--jb-danger)' },
  ];

  ngOnInit(): void {
    this.api.getDashboard().subscribe({
      next: (d) => {
        this.data = d;
        this.appliedThisWeek = d.appliedThisWeek ?? 7;
        this.remainingApps = Math.max(0, 10 - this.appliedThisWeek);
      },
      error: () => {}
    });
  }
}
