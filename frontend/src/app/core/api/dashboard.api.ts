import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DashboardData {
  recommendedJobs: unknown[];
  savedJobs: unknown[];
  pendingApplications: unknown[];
  upcomingInterviews: unknown[];
  weeklyMetrics: unknown;
  appliedThisWeek?: number;
  activeApplications?: number;
}

export interface DailyCount {
  date: string;
  count: number;
}

export interface WeeklyTrend {
  thisWeek: number;
  lastWeek: number;
  delta: number;
  message: string;
  daily: DailyCount[];
}

export interface DetailedMetrics {
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

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private http = inject(HttpClient);

  getDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>('/api/v1/dashboard');
  }

  getAnalytics(): Observable<unknown> {
    return this.http.get('/api/v1/analytics');
  }

  getDetailedAnalytics(): Observable<DetailedMetrics> {
    return this.http.get<DetailedMetrics>('/api/v1/analytics/detailed');
  }

  getWeeklyTrend(): Observable<WeeklyTrend> {
    return this.http.get<WeeklyTrend>('/api/v1/analytics/trend');
  }
}
