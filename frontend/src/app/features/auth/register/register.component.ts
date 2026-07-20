import { Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

function passwordsMatch(g: AbstractControl) {
  return g.get('password')?.value === g.get('confirmPassword')?.value
    ? null : { mismatch: true };
}

function strongPassword(c: AbstractControl) {
  const v: string = c.value ?? '';
  if (v.length < 8) return { weakPassword: 'must be at least 8 characters' };
  if (!/[A-Z]/.test(v)) return { weakPassword: 'needs an uppercase letter' };
  if (!/[0-9]/.test(v)) return { weakPassword: 'needs a number' };
  if (!/[^A-Za-z0-9]/.test(v)) return { weakPassword: 'needs a special character' };
  return null;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
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
      case 'auth/email-already-in-use': return 'An account with this email already exists.';
      case 'auth/invalid-email': return 'Invalid email address.';
      case 'auth/weak-password': return 'Password is too weak.';
      default: return 'Registration failed. Please try again.';
    }
  }
}
