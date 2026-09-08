import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Job, JobSearchResult, MatchResult } from '../models/job.model';
import { FeedbackType, RecommendationFeedback } from '../models/profile-section.model';
import { GeneratedDocument } from '../models/generated-document.model';

export interface IgnoredJob {
  id: string;
  userId: string;
  jobId: string;
  reason?: string;
  ignoredAt: string;
}

@Injectable({ providedIn: 'root' })
export class JobsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/jobs';

  getJobs(page = 0, size = 20): Observable<{ content: Job[]; totalElements: number }> {
    return this.http.get<{ content: Job[]; totalElements: number }>(this.base, { params: { page, size } });
  }

  search(q: string, page = 0, size = 20, categories: string[] = []): Observable<JobSearchResult> {
    const params: Record<string, string | number | string[]> = { q, page, size };
    if (categories.length) params['categories'] = categories;
    return this.http.get<JobSearchResult>(`${this.base}/search`, { params });
  }

  getById(id: string): Observable<Job> {
    return this.http.get<Job>(`${this.base}/${id}`);
  }

  /** Semantically similar active jobs (embedding nearest-neighbors). */
  getSimilar(id: string, limit = 5): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.base}/${id}/similar`, { params: { limit } });
  }

  /** Semantic search: query is embedded and ranked by vector distance. */
  searchSemantic(q: string, limit = 30): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.base}/search/semantic`, { params: { q, limit } });
  }

  getRecommendations(limit = 10): Observable<MatchResult[]> {
    return this.http.get<MatchResult[]>(`${this.base}/recommendations`, { params: { limit } });
  }

  save(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/save`, {});
  }

  ignore(id: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/ignore`, {}, reason ? { params: { reason } } : undefined);
  }

  getSaved(): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.base}/saved`);
  }

  unsave(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/save`);
  }

  submitFeedback(id: string, type: FeedbackType): Observable<RecommendationFeedback> {
    return this.http.post<RecommendationFeedback>(`${this.base}/${id}/feedback`, {}, { params: { type } });
  }

  removeFeedback(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/feedback`);
  }

  /** Hides the job for this user and asks the server to verify the posting URL. */
  reportInactive(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/report-inactive`, {});
  }

  getIgnored(): Observable<IgnoredJob[]> {
    return this.http.get<IgnoredJob[]>(`${this.base}/ignored`);
  }

  unignore(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}/ignore`);
  }

  getDocumentsForJob(id: string): Observable<GeneratedDocument[]> {
    return this.http.get<GeneratedDocument[]>(`${this.base}/${id}/documents`);
  }

  lookupByUrl(url: string): Observable<Job> {
    return this.http.get<Job>(`${this.base}/lookup`, { params: { url } });
  }

  addManual(payload: {
    title: string; companyName: string; description: string;
    url?: string; location?: string; employmentType?: string; remoteType?: string;
    salaryMin?: number; salaryMax?: number; currency?: string;
  }): Observable<Job> {
    return this.http.post<Job>(`${this.base}/manual`, payload);
  }
}
