import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FollowUpReminder {
  id: string;
  applicationId: string;
  userId: string;
  note: string | null;
  dueAt: string;
  completed: boolean;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class RemindersApiService {
  private http = inject(HttpClient);

  getDueReminders(): Observable<FollowUpReminder[]> {
    return this.http.get<FollowUpReminder[]>('/api/v1/reminders/due');
  }

  getOpenReminders(): Observable<FollowUpReminder[]> {
    return this.http.get<FollowUpReminder[]>('/api/v1/reminders');
  }

  getForApplication(applicationId: string): Observable<FollowUpReminder[]> {
    return this.http.get<FollowUpReminder[]>(`/api/v1/applications/${applicationId}/reminders`);
  }

  create(applicationId: string, note: string, dueAt: string): Observable<FollowUpReminder> {
    return this.http.post<FollowUpReminder>(`/api/v1/applications/${applicationId}/reminders`, {
      note,
      dueAt,
    });
  }

  complete(reminderId: string): Observable<FollowUpReminder> {
    return this.http.post<FollowUpReminder>(`/api/v1/reminders/${reminderId}/complete`, {});
  }

  delete(reminderId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/reminders/${reminderId}`);
  }
}
