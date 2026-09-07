import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Application, ApplicationStatus } from '../models/application.model';

export interface CreateApplicationPayload {
  jobId: string;
  cvVersionId?: string;
  promptTemplateId?: string;
  generatedDocumentId?: string;
  status?: ApplicationStatus;
  coverLetterText?: string;
  applicationText?: string;
  recruiterMessage?: string;
  matchScore?: number;
  notes?: string;
}

@Injectable({ providedIn: 'root' })
export class ApplicationsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/applications';

  getAll(): Observable<Application[]> {
    return this.http.get<Application[]>(this.base);
  }

  create(jobId: string, cvVersionId?: string, notes?: string): Observable<Application>;
  create(payload: CreateApplicationPayload): Observable<Application>;
  create(jobOrPayload: string | CreateApplicationPayload, cvVersionId?: string, notes?: string): Observable<Application> {
    const payload = typeof jobOrPayload === 'string'
      ? { jobId: jobOrPayload, cvVersionId, notes }
      : jobOrPayload;
    return this.http.post<Application>(this.base, payload);
  }

  getById(id: string): Observable<Application> {
    return this.http.get<Application>(`${this.base}/${id}`);
  }

  updateStatus(id: string, status: ApplicationStatus, notes?: string): Observable<Application> {
    return this.http.patch<Application>(`${this.base}/${id}/status`, { status, notes });
  }

  updateRecruiterInfo(
    id: string,
    data: { recruiterName?: string; recruiterEmail?: string; recruiterMessage?: string; recruiterReply?: string }
  ): Observable<Application> {
    return this.http.patch<Application>(`${this.base}/${id}/recruiter`, data);
  }

  attachGeneratedDocument(
    applicationId: string,
    generatedDocumentId: string,
    generatedContent?: string,
    status?: ApplicationStatus,
    notes?: string
  ): Observable<Application> {
    return this.http.post<Application>(
      `${this.base}/${applicationId}/generated-documents/${generatedDocumentId}`,
      { generatedContent, status, notes }
    );
  }
}
