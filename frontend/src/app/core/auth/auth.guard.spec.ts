import type { Mocked } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

/**
 * On a fresh page load Firebase has not yet restored the persisted session, so the guard
 * must wait for it before deciding — otherwise a signed-in user is bounced to /login.
 */
describe('authGuard', () => {
  let authenticated: boolean;
  let unverified: boolean;
  let resolveAuthReady: () => void;
  let authService: Mocked<AuthService>;

  beforeEach(() => {
    authenticated = false;
    unverified = false;
    authService = {
      whenAuthReady: vi.fn(), isAuthenticated: vi.fn(), needsEmailVerification: vi.fn(),
    } as unknown as Mocked<AuthService>;
    authService.whenAuthReady.mockReturnValue(new Promise<void>(resolve => (resolveAuthReady = resolve)));
    authService.isAuthenticated.mockImplementation(() => authenticated);
    authService.needsEmailVerification.mockImplementation(() => unverified);

    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    });
  });

  function runGuard(): Promise<boolean | UrlTree> {
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as Promise<boolean | UrlTree>;
  }

  it('lets a signed-in user through', async () => {
    authenticated = true;
    const result = runGuard();
    resolveAuthReady();

    expect(await result).toBe(true);
  });

  it('redirects a signed-out user to /login', async () => {
    const result = runGuard();
    resolveAuthReady();

    const tree = await result;
    expect(tree instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(tree as UrlTree)).toBe('/login');
  });

  it('sends a signed-in user with an unverified email to /verify-email', async () => {
    authenticated = true;
    unverified = true;
    const result = runGuard();
    resolveAuthReady();

    const tree = await result;
    expect(tree instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(tree as UrlTree)).toBe('/verify-email');
  });

  it('does not read the session before Firebase has restored it', async () => {
    authenticated = true;
    const result = runGuard();
    await Promise.resolve();

    expect(authService.isAuthenticated).not.toHaveBeenCalled();

    resolveAuthReady();
    expect(await result).toBe(true);
    expect(authService.isAuthenticated).toHaveBeenCalledTimes(1);
  });
});
