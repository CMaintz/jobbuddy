import { TestBed, fakeAsync, flushMicrotasks } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authInterceptor } from '../auth/auth.interceptor';
import { authGuard } from '../auth/auth.guard';
import { AuthService, MeResponse } from '../auth/auth.service';
import { DESKTOP_BACKEND_ORIGIN } from './desktop-shell';

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

    expect(TestBed.inject(AuthService).isAuthenticated()).toBeTrue();
  });

  it('offers no sign-out, which would only strand her on a login page', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DESKTOP_BACKEND_ORIGIN, useValue: BACKEND },
      ],
    });

    expect(TestBed.inject(AuthService).canSignOut()).toBeFalse();
  });
});

describe('desktop mode', () => {
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authService = jasmine.createSpyObj<AuthService>(
      'AuthService', ['getIdToken', 'whenAuthReady', 'isAuthenticated', 'ensureCurrentUser', 'isOnboardingComplete']);
    authService.getIdToken.and.resolveTo('firebase-token');
    authService.ensureCurrentUser.and.resolveTo(me(true));
    authService.isOnboardingComplete.and.returnValue(true);
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
    it('sends API calls to the backend, with no Firebase token', fakeAsync(() => {
      const http = TestBed.inject(HttpClient);
      const backend = TestBed.inject(HttpTestingController);
      http.get('/api/v1/jobs').subscribe();
      flushMicrotasks();

      const req = backend.expectOne(`${BACKEND}/api/v1/jobs`);
      expect(req.request.headers.has('Authorization')).toBeFalse();
      expect(authService.getIdToken).not.toHaveBeenCalled();
      req.flush([]);
      backend.verify();
    }));

    it('leaves the app\'s own files where they are', fakeAsync(() => {
      const http = TestBed.inject(HttpClient);
      const backend = TestBed.inject(HttpTestingController);
      http.get('./assets/i18n/da.json').subscribe();
      flushMicrotasks();

      backend.expectOne('./assets/i18n/da.json').flush({});
      backend.verify();
    }));
  });

  describe('authGuard', () => {
    function runGuard(url: string): Promise<boolean | UrlTree> {
      const state = { url } as RouterStateSnapshot;
      return TestBed.runInInjectionContext(
        () => authGuard({} as ActivatedRouteSnapshot, state)) as Promise<boolean | UrlTree>;
    }

    it('lets her in without a Firebase session', async () => {
      authService.isAuthenticated.and.returnValue(false);

      expect(await runGuard('/dashboard')).toBeTrue();
      expect(authService.whenAuthReady).not.toHaveBeenCalled();
    });

    it('starts a first-time user in onboarding, as signing in does', async () => {
      authService.ensureCurrentUser.and.resolveTo(me(false));
      authService.isOnboardingComplete.and.returnValue(false);

      const tree = await runGuard('/dashboard');
      expect(TestBed.inject(Router).serializeUrl(tree as UrlTree)).toBe('/onboarding');
      expect(await runGuard('/onboarding')).toBeTrue();
    });

    it('lets her out of onboarding once she has finished it, without a restart', async () => {
      // The record loaded at start still says "not done"; finishing onboarding updates the user
      // AuthService holds, and that is what counts.
      authService.ensureCurrentUser.and.resolveTo(me(false));
      authService.isOnboardingComplete.and.returnValue(true);

      expect(await runGuard('/dashboard')).toBeTrue();
    });
  });
});
