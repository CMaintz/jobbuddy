import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Computed "needs attention" item — not stored, recomputed by the backend on request. */
export interface Nudge {
  type: 'DEADLINE_SOON' | 'FOLLOW_UP';
  applicationId: string;
  jobId: string;
  jobTitle: string;
  companyName: string | null;
  /** DEADLINE_SOON only: the posting's application deadline (ISO date). */
  deadline: string | null;
  /** FOLLOW_UP only: days since the application was sent. */
  daysSinceApplied: number | null;
}

@Injectable({ providedIn: 'root' })
export class NudgesApiService {
  private http = inject(HttpClient);

  getNudges(): Observable<Nudge[]> {
    return this.http.get<Nudge[]>('/api/v1/users/me/nudges');
  }
}
