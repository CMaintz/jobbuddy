import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { EMPTY, catchError, combineLatest, distinctUntilChanged, filter, map, switchMap } from 'rxjs';
import { AuthService } from './auth/auth.service';
import { LanguageService } from './language.service';

/**
 * Keeps the server told which language the user reads the app in. The daily reminder email is
 * written server-side while the user is away, so it can only match the UI if the server knows.
 */
@Injectable({ providedIn: 'root' })
export class UiLanguageSyncService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private language = inject(LanguageService);
  private destroyRef = inject(DestroyRef);
  private lang$ = toObservable(this.language.lang);

  start(): void {
    combineLatest([this.auth.currentUser$, this.lang$]).pipe(
      filter(([user]) => !!user),
      map(([user, lang]) => ({ userId: user!.id, lang })),
      distinctUntilChanged((a, b) => a.userId === b.userId && a.lang === b.lang),
      // A failed sync only means the next email may be in the old language; never worth an error,
      // and it must not end the stream, or later switches would go unsent.
      switchMap(({ lang }) => this.http.put('/api/v1/users/me/language', { language: lang })
        .pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }
}
