import { Injectable, inject, signal } from '@angular/core';
import { Subscription, catchError, of } from 'rxjs';
import { ActivityStreak, DashboardApiService } from './api/dashboard.api';

const QUIET_WEEK: number[] = [0, 0, 0, 0, 0, 0, 0];
const NO_STREAK: ActivityStreak = { days: 0, lastWeek: QUIET_WEEK };

function localTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

@Injectable({ providedIn: 'root' })
export class ActivityStreakService {
  private api = inject(DashboardApiService);
  private request?: Subscription;

  readonly days = signal(0);
  readonly lastWeek = signal<number[]>(QUIET_WEEK);

  refresh(): void {
    this.request?.unsubscribe();
    this.request = this.api.getActivityStreak(localTimeZone()).pipe(
      catchError(() => of(NO_STREAK)),
    ).subscribe(streak => {
      this.days.set(streak.days);
      this.lastWeek.set(streak.lastWeek);
    });
  }

  clear(): void {
    this.request?.unsubscribe();
    this.days.set(0);
    this.lastWeek.set(QUIET_WEEK);
  }
}
