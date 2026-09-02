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

/** One thing the employer did — a screen, an interview, an offer, a rejection. */
export interface ResponseMetric {
  id: string;
  jobId: string | null;
  applicationId: string;
  eventType: string;
  eventAt: string;
  notes: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApplicationsApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/applications';

  /** What the employer did and when, oldest first. Empty until they respond. */
  timeline(applicationId: string): Observable<ResponseMetric[]> {
    return this.http.get<ResponseMetric[]>(`${this.base}/${applicationId}/timeline`);
  }

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

  /** Records outcome feedback + lessons; lessons feed future AI generations. */
  updateOutcome(id: string, data: { outcomeFeedback?: string; outcomeLessons?: string }): Observable<Application> {
    return this.http.patch<Application>(`${this.base}/${id}/outcome`, data);
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
