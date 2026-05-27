import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SkillGapResult {
  matched: string[];
  missing: string[];
  coveragePct: number;
}

@Injectable({ providedIn: 'root' })
export class SkillGapApiService {
  private http = inject(HttpClient);

  analyze(jobId: string): Observable<SkillGapResult> {
    return this.http.get<SkillGapResult>(`/api/v1/jobs/${jobId}/skill-gap`);
  }
}
