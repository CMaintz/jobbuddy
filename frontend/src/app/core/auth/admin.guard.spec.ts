import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

/** Developer screens (prompt library) must not open for a job seeker who types the URL. */
describe('adminGuard', () => {
  function runGuard(isAdmin: boolean): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: { isAdmin: () => isAdmin } }],
    });
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => adminGuard(route, state)) as boolean | UrlTree;
  }

  it('lets an admin in', () => {
    expect(runGuard(true)).toBeTrue();
  });

  it('sends anyone else to the dashboard', () => {
    const result = runGuard(false);

    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
