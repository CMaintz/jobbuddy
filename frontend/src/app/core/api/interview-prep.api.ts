import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface InterviewQuestion {
  id: string;
  jobId: string;
  userId: string;
  question: string;
  category: 'BEHAVIORAL' | 'TECHNICAL' | 'SITUATIONAL' | 'COMPANY';
  starAnswer: string | null;
  practiced: boolean;
  displayOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface InterviewPrepPack {
  questions: InterviewQuestion[];
  /** Claims from the submitted documents the candidate must be ready to defend. */
  consistencyBrief: string[];
  questionsToAsk: string[];
}

@Injectable({ providedIn: 'root' })
export class InterviewPrepApiService {
  private http = inject(HttpClient);

  getQuestions(jobId: string): Observable<InterviewQuestion[]> {
    return this.http.get<InterviewQuestion[]>(`/api/v1/jobs/${jobId}/interview-prep`);
  }

  addQuestion(jobId: string, question: string, category: string): Observable<InterviewQuestion> {
    return this.http.post<InterviewQuestion>(`/api/v1/jobs/${jobId}/interview-prep`, { question, category });
  }

  generateQuestions(jobId: string, jobDescription: string, count = 10): Observable<InterviewQuestion[]> {
    return this.http.post<InterviewQuestion[]>(`/api/v1/jobs/${jobId}/interview-prep/generate`, {
      jobDescription,
      count,
    });
  }

  /** Full prep pack: gap-targeted questions (persisted) + consistency brief + questions to ask. */
  generatePrepPack(jobId: string): Observable<InterviewPrepPack> {
    return this.http.post<InterviewPrepPack>(`/api/v1/jobs/${jobId}/interview-prep/pack`, {});
  }

  updateQuestion(jobId: string, questionId: string, patch: Partial<InterviewQuestion>): Observable<InterviewQuestion> {
    return this.http.put<InterviewQuestion>(`/api/v1/jobs/${jobId}/interview-prep/${questionId}`, patch);
  }

  deleteQuestion(jobId: string, questionId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/jobs/${jobId}/interview-prep/${questionId}`);
  }
}
