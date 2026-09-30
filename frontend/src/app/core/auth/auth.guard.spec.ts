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
  let resolveAuthReady: () => void;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authenticated = false;
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['whenAuthReady', 'isAuthenticated']);
    authService.whenAuthReady.and.returnValue(new Promise<void>(resolve => (resolveAuthReady = resolve)));
    authService.isAuthenticated.and.callFake(() => authenticated);

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

    expect(await result).toBeTrue();
  });

  it('redirects a signed-out user to /login', async () => {
    const result = runGuard();
    resolveAuthReady();

    const tree = await result;
    expect(tree instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(tree as UrlTree)).toBe('/login');
  });

  it('does not read the session before Firebase has restored it', async () => {
    authenticated = true;
    const result = runGuard();
    await Promise.resolve();

    expect(authService.isAuthenticated).not.toHaveBeenCalled();

    resolveAuthReady();
    expect(await result).toBeTrue();
    expect(authService.isAuthenticated).toHaveBeenCalledTimes(1);
  });
});
