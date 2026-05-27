import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentTheme, StructuredDocument } from '../models/structured-document.model';

export interface StructuredCvGenerateRequest {
  jobId?: string;
  jobDescription?: string;
  customInstructions?: string;
  targetLanguage?: string;
  templateId?: string;
  showProfileImage?: boolean;
  theme?: DocumentTheme;
}

export interface GenerateDocumentRequest {
  jobId?: string;
  jobDescription?: string;
  documentType: string;
  templateId?: string;
  customInstructions?: string;
  recruiterContext?: string;    // recruiter reply — included as additional context
  targetLanguage?: string;
  showProfileImage?: boolean;
  theme?: DocumentTheme;
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

  saveStructuredDocument(document: StructuredDocument, jobId?: string): Observable<StructuredDocument> {
    return this.http.post<StructuredDocument>('/api/v1/ai/documents/structured', { document, jobId });
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
