import { Injectable, inject, signal } from '@angular/core';
import { Subscription, catchError, map, of } from 'rxjs';
import { DashboardApiService } from './api/dashboard.api';

export function localTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

/** The user's current activity streak, shared by the sidebar and the dashboard. 0 when unknown. */
@Injectable({ providedIn: 'root' })
export class ActivityStreakService {
  private api = inject(DashboardApiService);
  private request?: Subscription;

  readonly days = signal(0);

  refresh(): void {
    this.request?.unsubscribe();
    this.request = this.api.getActivityStreak(localTimeZone()).pipe(
      map(streak => streak.days),
      catchError(() => of(0)),
    ).subscribe(days => this.days.set(days));
  }

  clear(): void {
    this.request?.unsubscribe();
    this.days.set(0);
  }
}
