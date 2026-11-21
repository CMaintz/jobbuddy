import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Hiding a nav item is not enough: a bookmarked or typed URL would still open the screen. */
export const adminGuard: CanActivateFn = () => {
  if (inject(AuthService).isAdmin()) return true;
  return inject(Router).createUrlTree(['/dashboard']);
};
