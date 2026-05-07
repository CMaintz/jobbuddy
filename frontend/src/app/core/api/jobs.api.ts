import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Job, JobSearchResult, MatchResult } from '../models/job.model';

@Injectable({ providedIn: 'root' })
export class JobsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/jobs';

  getJobs(page = 0, size = 20): Observable<{ content: Job[]; totalElements: number }> {
    return this.http.get<any>(this.base, { params: { page, size } });
  }

  search(q: string, page = 0, size = 20): Observable<JobSearchResult> {
    return this.http.get<JobSearchResult>(`${this.base}/search`, { params: { q, page, size } });
  }

  getById(id: string): Observable<Job> {
    return this.http.get<Job>(`${this.base}/${id}`);
  }

  getRecommendations(limit = 10): Observable<MatchResult[]> {
    return this.http.get<MatchResult[]>(`${this.base}/recommendations`, { params: { limit } });
  }

  save(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/save`, {});
  }

  ignore(id: string, reason?: string): Observable<void> {
    const params = reason ? { reason } : {};
    return this.http.post<void>(`${this.base}/${id}/ignore`, {}, { params });
  }
}
