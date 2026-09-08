import { Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../core/auth/auth.service';

function passwordsMatch(g: AbstractControl) {
  return g.get('password')?.value === g.get('confirmPassword')?.value
    ? null : { mismatch: true };
}

function strongPassword(c: AbstractControl) {
  const v: string = c.value ?? '';
  if (v.length < 8) return { weakPassword: 'auth.register.pwLength' };
  if (!/[A-Z]/.test(v)) return { weakPassword: 'auth.register.pwUppercase' };
  if (!/[0-9]/.test(v)) return { weakPassword: 'auth.register.pwNumber' };
  if (!/[^A-Za-z0-9]/.test(v)) return { weakPassword: 'auth.register.pwSpecial' };
  return null;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslateModule],
  templateUrl: './register.component.html'
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, strongPassword]],
    confirmPassword: ['', Validators.required]
  }, { validators: passwordsMatch });

  error = '';
  loading = false;
  success = false;
  registeredEmail = '';

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const { email, password } = this.form.value;
    this.authService.register(email!, password!).then(() => {
      this.registeredEmail = email!;
      this.success = true;
    }).catch((e: { code?: string }) => {
      this.error = this.friendlyError(e.code);
    }).finally(() => {
      this.loading = false;
    });
  }

  signInWithLinkedIn(): void {
    const redirectUri = window.location.origin + '/auth/linkedin/callback';
    window.location.href = this.authService.buildLinkedInAuthUrl(redirectUri);
  }

  private friendlyError(code?: string): string {
    switch (code) {
      case 'auth/email-already-in-use': return 'auth.error.emailInUse';
      case 'auth/invalid-email': return 'auth.error.invalidEmail';
      case 'auth/weak-password': return 'auth.error.weakPassword';
      default: return 'auth.error.registerFailed';
    }
  }
}
