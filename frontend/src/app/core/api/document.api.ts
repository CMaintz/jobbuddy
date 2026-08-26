import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CvVersion } from '../models/cv-version.model';

/** One dimension of a recorded document quality score. */
export interface QualityDimension {
  code: string;
  score: number;
  weight: number;
  detail: string;
}

export interface RecordedQualityScore {
  id: string;
  generatedDocumentId: string | null;
  documentType: string;
  score: { total: number; dimensions: QualityDimension[] };
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private http = inject(HttpClient);

  /** Recent document quality scores, newest first — the trend behind the number. */
  getQualityScores(limit = 20): Observable<RecordedQualityScore[]> {
    return this.http.get<RecordedQualityScore[]>('/api/v1/documents/quality-scores', { params: { limit } });
  }

  getCvVersions(): Observable<CvVersion[]> {
    return this.http.get<CvVersion[]>('/api/v1/cv');
  }

  uploadCv(name: string, content: string, format = 'MARKDOWN'): Observable<CvVersion> {
    return this.http.post<CvVersion>('/api/v1/cv', { name, content, format });
  }
}
