import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html'
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  error = '';
  loading = false;

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const { email, password } = this.form.value;
    this.authService.login(email!, password!).subscribe({
      next: (res) => this.router.navigate([res.onboardingComplete ? '/dashboard' : '/onboarding']),
      error: (e) => {
        this.error = this.friendlyError(e?.code);
        this.loading = false;
      }
    });
  }

  signInWithLinkedIn(): void {
    const redirectUri = window.location.origin + '/auth/linkedin/callback';
    window.location.href = this.authService.buildLinkedInAuthUrl(redirectUri);
  }

  private friendlyError(code: string): string {
    switch (code) {
      case 'auth/invalid-credential':
      case 'auth/wrong-password':
      case 'auth/user-not-found': return 'Invalid email or password.';
      case 'auth/user-disabled': return 'This account has been disabled.';
      case 'auth/too-many-requests': return 'Too many attempts. Please try again later.';
      default: return 'Sign in failed. Please try again.';
    }
  }
}
