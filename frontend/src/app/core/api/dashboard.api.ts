import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DashboardData {
  recommendedJobs: any[];
  savedJobs: any[];
  pendingApplications: any[];
  upcomingInterviews: any[];
  weeklyMetrics: any;
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

@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private http = inject(HttpClient);

  getDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>('/api/v1/dashboard');
  }

  getAnalytics(): Observable<any> {
    return this.http.get('/api/v1/analytics');
  }

  getWeeklyTrend(): Observable<WeeklyTrend> {
    return this.http.get<WeeklyTrend>('/api/v1/analytics/trend');
  }
}
