import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { StatCardComponent } from '../../shared/components/stat-card/stat-card.component';
import { FunnelComponent } from '../../shared/components/funnel/funnel.component';
import { HeatmapComponent } from '../../shared/components/heatmap/heatmap.component';
import { SparklineComponent } from '../../shared/components/sparkline/sparkline.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { DashboardApiService, DetailedMetrics, WeeklyTrend , FunnelTransition } from '../../core/api/dashboard.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { buildFunnelStages, buildHeatmapData, FunnelStage } from '../../shared/utils/application-insights';
import { stageLabelKey } from '../../shared/components/status-chip/status-chip.component';

const HEATMAP_WEEKS = 14;

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule, TranslateModule, JbIconComponent, JbTopbarComponent, StatCardComponent, FunnelComponent, HeatmapComponent, SparklineComponent, CompanyMarkComponent],
  templateUrl: './analytics.component.html'
})
export class AnalyticsComponent implements OnInit {
  private dashboardApi = inject(DashboardApiService);
  private appsApi = inject(ApplicationsApiService);

  loading = signal(true);
  loadError = signal(false);
  metrics = signal<DetailedMetrics | null>(null);
  trend = signal<WeeklyTrend | null>(null);
  funnelStages = signal<FunnelStage[]>([]);
  heatmapData = signal<number[]>([]);
  /** How long each stage transition takes on average, busiest transition first. */
  velocity = signal<FunnelTransition[]>([]);
  heatmapWeeks = HEATMAP_WEEKS;

  ngOnInit(): void {
    forkJoin({
      metrics: this.dashboardApi.getDetailedAnalytics(),
      trend: this.dashboardApi.getWeeklyTrend(),
      apps: this.appsApi.getAll(),
      velocity: this.dashboardApi.getFunnelVelocity(),
    }).subscribe({
      next: ({ metrics, trend, apps, velocity }) => {
        this.metrics.set(metrics);
        this.trend.set(trend);
        this.funnelStages.set(buildFunnelStages(apps));
        this.heatmapData.set(buildHeatmapData(apps, HEATMAP_WEEKS));
        this.velocity.set([...velocity.transitions].sort((a, b) => b.count - a.count));
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

  /** Stage transitions reuse the pipeline's stage names. */
  stageLabel(status: string): string {
    return stageLabelKey(status);
  }

  /** Widest bar is the slowest transition; the rest are relative to it. */
  velocityBar(t: FunnelTransition): number {
    const slowest = Math.max(...this.velocity().map(v => v.avgDays), 0.1);
    return Math.round((t.avgDays / slowest) * 100);
  }

  /** Sub-day gaps read as "same day" rather than "0.3 days". */
  days(value: number): string {
    return value < 1 ? '<1' : value.toFixed(value < 10 ? 1 : 0);
  }

  pct(value: number | undefined): string {
    return value !== undefined ? `${Math.round(value * 100)}%` : '—';
  }

}
