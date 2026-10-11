import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { AuthService, MeResponse } from '../../../core/auth/auth.service';
import { LinkedInCallbackComponent } from './linkedin-callback.component';

describe('LinkedInCallbackComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;

  function run(
    query: Record<string, string>, stateMatches: boolean, failStatus?: number,
  ): LinkedInCallbackComponent {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['linkedinCallback', 'consumeLinkedInState']);
    authService.consumeLinkedInState.and.returnValue(stateMatches);
    authService.linkedinCallback.and.returnValue(failStatus
      ? throwError(() => new HttpErrorResponse({ status: failStatus }))
      : of({ onboardingComplete: true } as MeResponse));
    TestBed.configureTestingModule({
      imports: [LinkedInCallbackComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const component = TestBed.createComponent(LinkedInCallbackComponent).componentInstance;
    component.ngOnInit();
    return component;
  }

  it('exchanges the code when the state matches', () => {
    const component = run({ code: 'abc', state: 'issued' }, true);

    expect(authService.consumeLinkedInState).toHaveBeenCalledWith('issued');
    expect(authService.linkedinCallback).toHaveBeenCalled();
    expect(component.error).toBe('');
  });

  it('rejects a mismatched state without exchanging the code', () => {
    const component = run({ code: 'abc', state: 'forged' }, false);

    expect(authService.linkedinCallback).not.toHaveBeenCalled();
    expect(component.error).toBe('auth.error.linkedInState');
  });

  it('tells the user to verify their LinkedIn email when the backend refuses it', () => {
    const component = run({ code: 'abc', state: 'issued' }, true, 403);

    expect(component.error).toBe('auth.error.linkedInEmailUnverified');
  });

  it('explains an existing unverified account holding the email', () => {
    const component = run({ code: 'abc', state: 'issued' }, true, 409);

    expect(component.error).toBe('auth.error.linkedInAccountUnverified');
  });

  it('rejects a callback with no state', () => {
    const component = run({ code: 'abc' }, false);

    expect(authService.consumeLinkedInState).toHaveBeenCalledWith(null);
    expect(authService.linkedinCallback).not.toHaveBeenCalled();
    expect(component.error).toBe('auth.error.linkedInState');
  });
});
