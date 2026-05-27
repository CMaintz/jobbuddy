import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResumeDraft, ResumeData, ResumeSettings } from '../models/resume-builder.models';

export interface SaveDraftRequest {
  name?: string;
  jobId?: string;
  applicationId?: string;
  resumeData?: ResumeData;
  settings?: ResumeSettings;
}

@Injectable({ providedIn: 'root' })
export class ResumeDraftApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/resume-drafts';

  getDrafts(): Observable<ResumeDraft[]> {
    return this.http.get<ResumeDraft[]>(this.base);
  }

  getDraft(id: string): Observable<ResumeDraft> {
    return this.http.get<ResumeDraft>(`${this.base}/${id}`);
  }

  getDraftByApplication(applicationId: string): Observable<ResumeDraft> {
    return this.http.get<ResumeDraft>(`${this.base}/by-application/${applicationId}`);
  }

  createDraft(data: Partial<ResumeDraft>): Observable<ResumeDraft> {
    return this.http.post<ResumeDraft>(this.base, data);
  }

  saveDraft(id: string, data: SaveDraftRequest): Observable<ResumeDraft> {
    return this.http.put<ResumeDraft>(`${this.base}/${id}`, data);
  }

  publishDraft(id: string): Observable<ResumeDraft> {
    return this.http.put<ResumeDraft>(`${this.base}/${id}/publish`, {});
  }

  deleteDraft(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
