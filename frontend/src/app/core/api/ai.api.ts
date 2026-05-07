import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GenerateRequest {
  jobId?: string;
  cvVersionId?: string;
  promptTemplateId?: string;
  documentType: 'COVER_LETTER' | 'APPLICATION_TEXT' | 'RECRUITER_MESSAGE' | 'CV_ANALYSIS_REPORT';
  customInstructions?: string;
}

export interface GenerateResponse {
  content: string;
  modelUsed: string;
  tokensUsed?: number;
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

  analyzeCv(cvVersionId: string, jobId?: string): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>('/api/v1/ai/analyze', { cvVersionId, jobId });
  }
}
