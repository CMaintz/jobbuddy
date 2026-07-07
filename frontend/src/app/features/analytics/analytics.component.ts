import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { FunnelComponent } from '../../shared/components/funnel/funnel.component';
import { HeatmapComponent } from '../../shared/components/heatmap/heatmap.component';
import { SparklineComponent } from '../../shared/components/sparkline/sparkline.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { DashboardApiService, DetailedMetrics, WeeklyTrend } from '../../core/api/dashboard.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';

const HEATMAP_WEEKS = 14;

const FUNNEL_ORDER: { statuses: ApplicationStatus[]; label: string; color: string }[] = [
  { statuses: ['SAVED', 'PREPARING'], label: 'Saved', color: 'var(--jb-text-dim)' },
  { statuses: ['APPLIED'], label: 'Applied', color: 'var(--jb-info)' },
  { statuses: ['RECRUITER_CONTACT'], label: 'Screen', color: 'var(--jb-violet)' },
  { statuses: ['INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND'], label: 'Interview', color: 'var(--jb-accent)' },
  { statuses: ['OFFER'], label: 'Offer', color: 'var(--jb-success)' },
];

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, JbIconComponent, StatCardComponent, FunnelComponent, HeatmapComponent, SparklineComponent, CompanyMarkComponent],
  templateUrl: './analytics.component.html'
})
export class AnalyticsComponent implements OnInit {
  private dashboardApi = inject(DashboardApiService);
  private appsApi = inject(ApplicationsApiService);

  loading = signal(true);
  loadError = signal(false);
  metrics = signal<DetailedMetrics | null>(null);
  trend = signal<WeeklyTrend | null>(null);
  funnelStages = signal<{ label: string; value: number; color: string }[]>([]);
  heatmapData = signal<number[]>([]);
  heatmapWeeks = HEATMAP_WEEKS;

  ngOnInit(): void {
    forkJoin({
      metrics: this.dashboardApi.getDetailedAnalytics(),
      trend: this.dashboardApi.getWeeklyTrend(),
      apps: this.appsApi.getAll(),
    }).subscribe({
      next: ({ metrics, trend, apps }) => {
        this.metrics.set(metrics);
        this.trend.set(trend);
        this.funnelStages.set(this.buildFunnel(apps));
        this.heatmapData.set(this.buildHeatmap(apps));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      }
    });
  }

  trendData(): number[] {
    return (this.trend()?.daily ?? []).map(d => d.count);
  }

  pct(value: number | undefined): string {
    return value !== undefined ? `${Math.round(value * 100)}%` : '—';
  }

  /**
   * Cumulative funnel: each stage counts applications that reached it or any
   * later stage, so the funnel always narrows.
   */
  private buildFunnel(apps: Application[]): { label: string; value: number; color: string }[] {
    const stageIndex = (status: ApplicationStatus): number =>
      FUNNEL_ORDER.findIndex(s => s.statuses.includes(status));
    return FUNNEL_ORDER.map((stage, i) => ({
      label: stage.label,
      value: apps.filter(a => {
        const idx = stageIndex(a.status);
        return idx >= i || (a.status === 'REJECTED' && i <= 1); // rejected apps still passed saved/applied
      }).length,
      color: stage.color,
    }));
  }

  /** Applications created per day over the last N weeks, oldest first. */
  private buildHeatmap(apps: Application[]): number[] {
    const days = HEATMAP_WEEKS * 7;
    const counts = new Array<number>(days).fill(0);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    for (const app of apps) {
      const created = new Date(app.appliedAt ?? app.createdAt);
      created.setHours(0, 0, 0, 0);
      const diff = Math.floor((today.getTime() - created.getTime()) / 86400000);
      if (diff >= 0 && diff < days) counts[days - 1 - diff]++;
    }
    return counts;
  }
}
