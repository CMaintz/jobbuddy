import type { MockInstance, Mocked } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AuthService, MeResponse } from '../../../core/auth/auth.service';
import { VerifyEmailComponent } from './verify-email.component';

describe('VerifyEmailComponent', () => {
  let authService: Mocked<AuthService>;
  let navigate: MockInstance<Router['navigate']>;

  async function create(signedIn = true, unverified = true): Promise<VerifyEmailComponent> {
    authService = {
      whenAuthReady: vi.fn(), isAuthenticated: vi.fn(), needsEmailVerification: vi.fn(), currentEmail: vi.fn(),
      resendVerification: vi.fn(), confirmEmailVerified: vi.fn(), loadCurrentUser: vi.fn(), logout: vi.fn(),
    } as unknown as Mocked<AuthService>;
    authService.whenAuthReady.mockResolvedValue();
    authService.isAuthenticated.mockReturnValue(signedIn);
    authService.needsEmailVerification.mockReturnValue(unverified);
    authService.currentEmail.mockReturnValue('me@example.com');
    TestBed.configureTestingModule({
      imports: [VerifyEmailComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const component = TestBed.createComponent(VerifyEmailComponent).componentInstance;
    await component.ngOnInit();
    return component;
  }

  it('shows the address the link was sent to', async () => {
    const component = await create();

    expect(component.email()).toBe('me@example.com');
  });

  it('sends a signed-out visitor to the login page', async () => {
    await create(false);

    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sends an already verified user on into the app', async () => {
    await create(true, false);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('resends the verification email and then cools down', async () => {
    const component = await create();
    authService.resendVerification.mockResolvedValue();

    await component.resend();
    await component.resend();

    expect(authService.resendVerification).toHaveBeenCalledTimes(1);
    expect(component.cooldown()).toBeGreaterThan(0);
  });

  it('explains a rate-limited resend instead of a generic failure', async () => {
    const component = await create();
    authService.resendVerification.mockRejectedValue({ code: 'auth/too-many-requests' });

    await component.resend();

    expect(component.status()).toBe('tooManyRequests');
    expect(component.cooldown()).toBeGreaterThan(0);
  });

  it('resends the verification email', async () => {
    const component = await create();
    authService.resendVerification.mockResolvedValue();

    await component.resend();

    expect(authService.resendVerification).toHaveBeenCalled();
    expect(component.status()).toBe('sent');
  });

  it('reports a failed resend', async () => {
    const component = await create();
    authService.resendVerification.mockRejectedValue(new Error('quota'));

    await component.resend();

    expect(component.status()).toBe('sendFailed');
    expect(component.busy()).toBe(false);
  });

  it('stays put while the email is still unverified', async () => {
    const component = await create();
    authService.confirmEmailVerified.mockResolvedValue(false);

    await component.continue();

    expect(component.status()).toBe('stillUnverified');
    expect(authService.loadCurrentUser).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('continues into the app once verified', async () => {
    const component = await create();
    authService.confirmEmailVerified.mockResolvedValue(true);
    authService.loadCurrentUser.mockReturnValue(of({ onboardingComplete: false } as MeResponse));

    await component.continue();

    expect(navigate).toHaveBeenCalledWith(['/onboarding']);
  });
});
