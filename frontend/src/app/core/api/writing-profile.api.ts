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
}
