import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { DESKTOP_BACKEND_ORIGIN } from '../desktop/desktop-shell';

const API_PREFIX = '/api/';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const desktopBackend = inject(DESKTOP_BACKEND_ORIGIN);
  if (desktopBackend) {
    // The desktop shell adds its own token to requests for this origin; there is no Firebase.
    return next(req.url.startsWith(API_PREFIX) ? req.clone({ url: desktopBackend + req.url }) : req);
  }
  const authService = inject(AuthService);
  return from(authService.getIdToken()).pipe(
    switchMap(token => {
      if (token) {
        req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
      }
      return next(req);
    })
  );
};
