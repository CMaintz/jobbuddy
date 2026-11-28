import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';
import { LoginComponent } from './login.component';
import type { Mocked } from 'vitest';
import { spyObj } from '../../../../testing/spy-obj';

describe('LoginComponent password reset', () => {
  let authService: Mocked<AuthService>;

  function create(email: string): LoginComponent {
    authService = spyObj<AuthService>([
      'login', 'sendPasswordReset', 'buildLinkedInAuthUrl',
    ]);
    TestBed.configureTestingModule({
      imports: [LoginComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }],
    });
    const component = TestBed.createComponent(LoginComponent).componentInstance;
    component.form.controls.email.setValue(email);
    return component;
  }

  it('sends the reset link to the address typed on the login page', async () => {
    const component = create('me@example.com');
    authService.sendPasswordReset.mockResolvedValue(undefined);

    await component.sendPasswordReset();

    expect(authService.sendPasswordReset).toHaveBeenCalledWith('me@example.com');
    expect(component.resetStatus()).toBe('sent');
  });

  it('asks for the email first instead of sending to an empty address', async () => {
    const component = create('');

    await component.sendPasswordReset();

    expect(authService.sendPasswordReset).not.toHaveBeenCalled();
    expect(component.resetStatus()).toBe('needEmail');
  });

  it('answers an unknown address like a known one, so it does not reveal who has an account',
    async () => {
      const component = create('nobody@example.com');
      authService.sendPasswordReset.mockRejectedValue({ code: 'auth/user-not-found' });

      await component.sendPasswordReset();

      expect(component.resetStatus()).toBe('sent');
    });
});
