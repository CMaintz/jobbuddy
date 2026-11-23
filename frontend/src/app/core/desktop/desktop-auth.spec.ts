import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authInterceptor } from '../auth/auth.interceptor';
import { authGuard } from '../auth/auth.guard';
import { AuthService, MeResponse } from '../auth/auth.service';
import { DESKTOP_BACKEND_ORIGIN } from './desktop-shell';
import type { Mocked } from 'vitest';
import { spyObj } from '../../../testing/spy-obj';
import { nextMacrotask } from '../../../testing/next-macrotask';

/**
 * Inside the Windows app the page is served from app://jobbuddy and there is no Firebase: the
 * shell signs every backend request in with its own token. Relative /api URLs and the Firebase
 * login would both leave her staring at a login screen she has no account for.
 */
const BACKEND = 'http://127.0.0.1:51234';

function me(onboardingComplete: boolean): MeResponse {
  return { userId: 'u1', email: 'local@jobbuddy.invalid', role: 'USER', onboardingComplete };
}

describe('AuthService in desktop mode', () => {
  it('counts her as signed in, so the app shows its navigation, not the login layout', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DESKTOP_BACKEND_ORIGIN, useValue: BACKEND },
      ],
    });

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
  });

  it('offers no sign-out, which would only strand her on a login page', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DESKTOP_BACKEND_ORIGIN, useValue: BACKEND },
      ],
    });

    expect(TestBed.inject(AuthService).canSignOut()).toBe(false);
  });
});

describe('desktop mode', () => {
  let authService: Mocked<AuthService>;

  beforeEach(() => {
    authService = spyObj<AuthService>(['getIdToken', 'whenAuthReady', 'isAuthenticated', 'ensureCurrentUser', 'isOnboardingComplete']);
    authService.getIdToken.mockResolvedValue('firebase-token');
    authService.ensureCurrentUser.mockResolvedValue(me(true));
    authService.isOnboardingComplete.mockReturnValue(true);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withXhr(), withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: DESKTOP_BACKEND_ORIGIN, useValue: BACKEND },
      ],
    });
  });

  describe('authInterceptor', () => {
    it('sends API calls to the backend, with no Firebase token', async () => {
      const http = TestBed.inject(HttpClient);
      const backend = TestBed.inject(HttpTestingController);
      http.get('/api/v1/jobs').subscribe();
      await nextMacrotask();

      const req = backend.expectOne(`${BACKEND}/api/v1/jobs`);
      expect(req.request.headers.has('Authorization')).toBe(false);
      expect(authService.getIdToken).not.toHaveBeenCalled();
      req.flush([]);
      backend.verify();
    });

    it('leaves the app\'s own files where they are', async () => {
      const http = TestBed.inject(HttpClient);
      const backend = TestBed.inject(HttpTestingController);
      http.get('./assets/i18n/da.json').subscribe();
      await nextMacrotask();

      backend.expectOne('./assets/i18n/da.json').flush({});
      backend.verify();
    });
  });

  describe('authGuard', () => {
    function runGuard(url: string): Promise<boolean | UrlTree> {
      const state = { url } as RouterStateSnapshot;
      return TestBed.runInInjectionContext(
        () => authGuard({} as ActivatedRouteSnapshot, state)) as Promise<boolean | UrlTree>;
    }

    it('lets her in without a Firebase session', async () => {
      authService.isAuthenticated.mockReturnValue(false);

      expect(await runGuard('/dashboard')).toBe(true);
      expect(authService.whenAuthReady).not.toHaveBeenCalled();
    });

    it('starts a first-time user in onboarding, as signing in does', async () => {
      authService.ensureCurrentUser.mockResolvedValue(me(false));
      authService.isOnboardingComplete.mockReturnValue(false);

      const tree = await runGuard('/dashboard');
      expect(TestBed.inject(Router).serializeUrl(tree as UrlTree)).toBe('/onboarding');
      expect(await runGuard('/onboarding')).toBe(true);
    });

    it('lets her out of onboarding once she has finished it, without a restart', async () => {
      // The record loaded at start still says "not done"; finishing onboarding updates the user
      // AuthService holds, and that is what counts.
      authService.ensureCurrentUser.mockResolvedValue(me(false));
      authService.isOnboardingComplete.mockReturnValue(true);

      expect(await runGuard('/dashboard')).toBe(true);
    });
  });
});
