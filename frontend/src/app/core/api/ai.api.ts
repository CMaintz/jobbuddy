import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StructuredDocument } from '../models/structured-document.model';

export interface GenerateRequest {
  jobId?: string;
  jobDescription?: string;
  cvVersionId?: string;
  promptTemplateId?: string;
  documentType: 'COVER_LETTER' | 'APPLICATION_TEXT' | 'RECRUITER_MESSAGE' | 'CV_ANALYSIS_REPORT' | 'CV' | 'FOLLOW_UP_MESSAGE';
  customInstructions?: string;
  targetLanguage?: string;
  useStyleFromHistory?: boolean;
}

export interface StructuredCvGenerateRequest {
  jobId?: string;
  jobDescription?: string;
  customInstructions?: string;
  targetLanguage?: string;
  templateId?: string;
}

export interface GenerateDocumentRequest {
  jobId?: string;
  jobDescription?: string;
  documentType: string;
  templateId?: string;
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

  getCvRenderModel(templateId?: string): Observable<StructuredDocument> {
    const params: Record<string, string> = {};
    if (templateId) params['templateId'] = templateId;
    return this.http.get<StructuredDocument>('/api/v1/ai/cv/render-model', { params });
  }

  generateDocument(req: GenerateDocumentRequest): Observable<StructuredDocument> {
    return this.http.post<StructuredDocument>('/api/v1/ai/generate-document', req);
  }

  generateStructuredCv(req: StructuredCvGenerateRequest): Observable<StructuredDocument> {
    return this.http.post<StructuredDocument>('/api/v1/ai/cv/generate-structured', req);
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
