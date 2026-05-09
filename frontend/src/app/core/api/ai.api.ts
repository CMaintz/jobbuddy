import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GenerateRequest {
  jobId?: string;
  cvVersionId?: string;
  promptTemplateId?: string;
  documentType: 'COVER_LETTER' | 'APPLICATION_TEXT' | 'RECRUITER_MESSAGE' | 'CV_ANALYSIS_REPORT' | 'CV' | 'FOLLOW_UP_MESSAGE';
  customInstructions?: string;
  targetLanguage?: string;
  useStyleFromHistory?: boolean;
}

export interface GenerateResponse {
  content: string;
  modelUsed: string;
  tokensUsed?: number;
}

export interface RefineRequest {
  currentContent: string;
  userMessage: string;
  jobDescription?: string;
  targetLanguage?: string;
}

export interface RefineResponse {
  refinedContent: string;
  modelUsed: string;
}

export interface AnalysisResponse {
  suggestions: string[];
  score: number;
  rawResponse: string;
}

@Injectable({ providedIn: 'root' })
export class AiApiService {
  private http = inject(HttpClient);

  generate(req: GenerateRequest): Observable<GenerateResponse> {
    return this.http.post<GenerateResponse>('/api/v1/ai/generate', req);
  }

  refine(req: RefineRequest): Observable<RefineResponse> {
    return this.http.post<RefineResponse>('/api/v1/ai/refine', req);
  }

  analyzeCv(cvVersionId: string, jobId?: string): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>('/api/v1/ai/analyze', { cvVersionId, jobId });
  }

  getDocuments(): Observable<any[]> {
    return this.http.get<any[]>('/api/v1/ai/documents');
  }
}
