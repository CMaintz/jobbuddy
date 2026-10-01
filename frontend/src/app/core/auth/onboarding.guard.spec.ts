import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { onboardingGuard } from './onboarding.guard';
import { AuthService } from './auth.service';

/** Onboarding is a one-way door: once finished, the route sends the user on to the dashboard. */
describe('onboardingGuard', () => {
  function runGuard(onboardingComplete: boolean): boolean | UrlTree {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isOnboardingComplete: () => onboardingComplete } },
      ],
    });
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => onboardingGuard(route, state)) as boolean | UrlTree;
  }

  it('allows onboarding while it is still unfinished', () => {
    expect(runGuard(false)).toBeTrue();
  });

  it('redirects to the dashboard once onboarding is complete', () => {
    const result = runGuard(true);

    expect(result instanceof UrlTree).toBeTrue();
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/dashboard');
  });
});
