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
import { buildFunnelStages, buildHeatmapData, FunnelStage } from '../../shared/utils/application-insights';

const HEATMAP_WEEKS = 14;

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
  funnelStages = signal<FunnelStage[]>([]);
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
        this.funnelStages.set(buildFunnelStages(apps));
        this.heatmapData.set(buildHeatmapData(apps, HEATMAP_WEEKS));
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

}
