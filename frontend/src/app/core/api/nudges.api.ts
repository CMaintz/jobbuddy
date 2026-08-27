import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Computed "needs attention" item — not stored, recomputed by the backend on request. */
export interface Nudge {
  type: 'DEADLINE_SOON' | 'FOLLOW_UP' | 'REMINDER_DUE' | 'OUTREACH_FOLLOW_UP';
  applicationId: string | null;
  jobId: string | null;
  /** OUTREACH_FOLLOW_UP only: the tracked outreach that is due. */
  outreachId: string | null;
  /** REMINDER_DUE only: the reminder the user set. */
  reminderId: string | null;
  jobTitle: string | null;
  companyName: string | null;
  /** The date it is due — set for everything except FOLLOW_UP. */
  deadline: string | null;
  /** FOLLOW_UP only: days since the application was sent. */
  daysSinceApplied: number | null;
  /** REMINDER_DUE: the user's own note. OUTREACH_FOLLOW_UP: who to contact. */
  note: string | null;
}

@Injectable({ providedIn: 'root' })
export class NudgesApiService {
  private http = inject(HttpClient);

  getNudges(): Observable<Nudge[]> {
    return this.http.get<Nudge[]>('/api/v1/users/me/nudges');
  }
}
