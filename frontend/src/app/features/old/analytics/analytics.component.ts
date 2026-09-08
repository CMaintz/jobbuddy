import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DailyCount, WeeklyTrend } from '../../../core/api/dashboard.api';

interface DetailedMetrics {
  total: number;
  saved: number;
  applied: number;
  pendingResponse: number;
  activeInterviews: number;
  offers: number;
  appliedThisWeek: number;
  appliedThisMonth: number;
  responseRate: number;
  interviewRate: number;
  offerRate: number;
  topCompanies: string[];
}

interface SparklineBar {
  x: number;
  y: number;
  height: number;
  isThisWeek: boolean;
}

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './analytics.component.html'
})
export class AnalyticsComponent implements OnInit {
  private http = inject(HttpClient);

  metrics: DetailedMetrics | null = null;
  loading = true;

  trend: WeeklyTrend | null = null;
  trendBars: SparklineBar[] = [];

  ngOnInit(): void {
    this.http.get<DetailedMetrics>('/api/v1/analytics/detailed').subscribe({
      next: (data) => {
        this.metrics = data;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });

    this.http.get<WeeklyTrend>('/api/v1/analytics/trend').subscribe({
      next: (data) => {
        this.trend = data;
        this.trendBars = this.computeBars(data.daily);
      },
      error: () => {}
    });
  }

  private computeBars(daily: DailyCount[]): SparklineBar[] {
    if (!daily?.length) return [];
    const maxCount = Math.max(...daily.map(d => d.count), 1);
    const CHART_H = 40;
    return daily.map((d, i) => {
      const h = Math.max(2, Math.round((d.count / maxCount) * CHART_H));
      return { x: i * 20, y: 44 - h, height: h, isThisWeek: i >= 7 };
    });
  }

  funnelWidth(value: number, total: number): string {
    if (!total || total === 0) return '0%';
    const pct = Math.min(100, Math.round((value / total) * 100));
    return `${pct}%`;
  }
}
