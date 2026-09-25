import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** The user's standing per-section CV tailoring prompts, keyed by section (e.g. 'profile'). */
export interface CvSectionPrompts {
  prompts: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class CvSectionPromptsApiService {
  private http = inject(HttpClient);

  get(): Observable<CvSectionPrompts> {
    return this.http.get<CvSectionPrompts>('/api/v1/users/me/cv-section-prompts');
  }

  /** A blank value for a section clears it. */
  save(prompts: Record<string, string>): Observable<CvSectionPrompts> {
    return this.http.put<CvSectionPrompts>('/api/v1/users/me/cv-section-prompts', { prompts });
  }
}
