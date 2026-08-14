import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DocumentTheme, StructuredDocument } from '../models/structured-document.model';
import { Profile } from '../models/user.model';
import { GeneratedDocument } from '../models/generated-document.model';

export interface StructuredCvGenerateRequest {
  jobId?: string;
  jobDescription?: string;
  customInstructions?: string;
  targetLanguage?: string;
  templateId?: string;
  promptTemplateId?: string;
  showProfileImage?: boolean;
  theme?: DocumentTheme;
}

export interface GenerateDocumentRequest {
  jobId?: string;
  jobDescription?: string;
  documentType: string;
  templateId?: string;
  promptTemplateId?: string;
  customInstructions?: string;
  motivationText?: string;
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

export interface ReviewRequest {
  currentContent: string;
  documentType?: string;
  jobDescription?: string;
  targetLanguage?: string;
}

export interface ReviewResponse {
  revisedContent: string;
  critique: string[];
  modelUsed: string;
}

export interface AnalysisDimensions {
  technicalSkills?: number;
  experience?: number;
  cultureFit?: number;
  careerAlignment?: number;
  /** PASS | FLAG | FAIL — unweighted veto, not part of the score. */
  location?: string;
  locationNote?: string;
}

export interface RiskSignal {
  label: string;
  /** LOW | MEDIUM | HIGH */
  severity: string;
  note?: string;
}

export interface RiskAssessment {
  /** HIGH_CONFIDENCE | CAUTION | SUSPICIOUS | NOT_ASSESSED */
  legitimacy?: string;
  legitimacyNote?: string;
  signals?: RiskSignal[];
  /** HIGH | MEDIUM | LOW | UNKNOWN */
  compensationReliability?: string;
  compensationNote?: string;
}

export interface AnalysisResponse {
  suggestions: string[];
  score: number;
  rawResponse: string;
  summary?: string;
  strengths?: string[];
  gaps?: string[];
  /** Present only for job-targeted analyses. */
  dimensions?: AnalysisDimensions;
  /** Posting/employer risk — separate from the score; job-targeted analyses only. */
  risk?: RiskAssessment;
}

export interface AnalyzeRequest {
  cvVersionId?: string;
  jobId?: string;
  jobDescription?: string;
}

export interface SkillGap {
  skill: string;
  /** Roughly how many analyzed postings ask for it. */
  demand: number;
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string;
  why?: string;
  resources?: string[];
}

export interface SkillGapReport {
  gaps: SkillGap[];
  summary?: string;
  jobsAnalyzed: number;
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

  /** Fresh-context reviewer pass: critiques the draft against the posting and revises it. */
  review(req: ReviewRequest): Observable<ReviewResponse> {
    return this.http.post<ReviewResponse>('/api/v1/ai/review', req);
  }

  analyzeCv(cvVersionId: string, jobId?: string): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>('/api/v1/ai/analyze', { cvVersionId, jobId });
  }

  /** Analyzes the master profile (no cvVersionId) against an optional job. */
  analyze(req: AnalyzeRequest): Observable<AnalysisResponse> {
    return this.http.post<AnalysisResponse>('/api/v1/ai/analyze', req);
  }

  /** Skill-gap heatmap across the pipeline + embedding-matched market jobs. */
  analyzeSkillGaps(): Observable<SkillGapReport> {
    return this.http.post<SkillGapReport>('/api/v1/ai/skill-gaps', {});
  }

  getDocuments(): Observable<GeneratedDocument[]> {
    return this.http.get<GeneratedDocument[]>('/api/v1/ai/documents');
  }

  parseCv(rawCvText: string): Observable<Profile> {
    return this.http.post<Profile>('/api/v1/ai/parse-cv', { rawCvText });
  }
}
