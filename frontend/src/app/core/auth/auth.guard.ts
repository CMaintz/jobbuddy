import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { DESKTOP_BACKEND_ORIGIN } from '../desktop/desktop-shell';

const ONBOARDING = '/onboarding';

export const authGuard: CanActivateFn = async (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (inject(DESKTOP_BACKEND_ORIGIN)) {
    // The shell has already signed her in; only a first start still owes her the onboarding
    // that signing in leads to on the web. The record loaded at start goes stale the moment she
    // finishes onboarding, so ask AuthService, which completeOnboarding() keeps current.
    await authService.ensureCurrentUser();
    if (!authService.isOnboardingComplete() && !state.url.startsWith(ONBOARDING)) {
      return router.createUrlTree([ONBOARDING]);
    }
    return true;
  }
  await authService.whenAuthReady();
  if (!authService.isAuthenticated()) return router.createUrlTree(['/login']);
  if (authService.needsEmailVerification()) return router.createUrlTree(['/verify-email']);
  return true;
};
