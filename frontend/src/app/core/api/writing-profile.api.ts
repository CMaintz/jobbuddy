import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface WritingProfile {
  id?: string;
  userId?: string;
  tone?: string;
  vocabularyNotes?: string;
  phrasingPatterns?: string[];
  exampleExcerpts?: string[];
  /** Style rules to always follow. */
  dos?: string[];
  /** Style rules to never break (banned phrases, clichés). */
  donts?: string[];
  /** How documents should be structured (paragraph order, length, sign-off). */
  structureNotes?: string;
  /** Set when the profile was last derived from writing samples; persisted on save. */
  lastAnalyzedAt?: string;
}

export interface AnalyzeStyleRequest {
  /** Pasted texts the user wrote themselves. */
  samples: string[];
  /** Generated documents to include as samples (resolved server-side). */
  documentIds: string[];
}

@Injectable({ providedIn: 'root' })
export class WritingProfileApiService {
  private http = inject(HttpClient);
  private base = '/api/v1/users/me/writing-style';

  get(): Observable<WritingProfile> {
    return this.http.get<WritingProfile>(this.base);
  }

  update(profile: WritingProfile): Observable<WritingProfile> {
    return this.http.put<WritingProfile>(this.base, profile);
  }

  /** Returns a proposed profile derived from the samples — nothing is persisted until update(). */
  analyze(req: AnalyzeStyleRequest): Observable<WritingProfile> {
    return this.http.post<WritingProfile>(`${this.base}/analyze`, req);
  }
}
