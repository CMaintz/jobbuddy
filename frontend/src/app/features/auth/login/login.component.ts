import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="min-h-screen flex items-center justify-center">
      <div class="card w-full max-w-md">
        <h1 class="text-2xl font-bold text-gray-900 mb-6">Sign in to AutoApplicant</h1>
        @if (error) {
          <div class="bg-red-50 text-red-700 rounded-md p-3 mb-4 text-sm">{{ error }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label">Email</label>
            <input type="email" formControlName="email" class="input" placeholder="you@example.com" />
          </div>
          <div>
            <label class="label">Password</label>
            <input type="password" formControlName="password" class="input" placeholder="••••••••" />
          </div>
          <button type="submit" [disabled]="form.invalid || loading" class="btn-primary w-full">
            {{ loading ? 'Signing in...' : 'Sign in' }}
          </button>
        </form>
        <div class="mt-4 relative">
          <div class="absolute inset-0 flex items-center">
            <div class="w-full border-t border-gray-200"></div>
          </div>
          <div class="relative flex justify-center text-xs uppercase">
            <span class="bg-white px-2 text-gray-400">or</span>
          </div>
        </div>
        <button (click)="signInWithLinkedIn()" type="button"
            class="mt-3 w-full flex items-center justify-center gap-2 border border-gray-300 rounded-md px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="#0A66C2" viewBox="0 0 24 24">
            <path d="M20.447 20.452h-3.554v-5.569c0-1.328-.027-3.037-1.852-3.037-1.853 0-2.136 1.445-2.136 2.939v5.667H9.351V9h3.414v1.561h.046c.477-.9 1.637-1.85 3.37-1.85 3.601 0 4.267 2.37 4.267 5.455v6.286zM5.337 7.433a2.062 2.062 0 01-2.063-2.065 2.064 2.064 0 112.063 2.065zm1.782 13.019H3.555V9h3.564v11.452zM22.225 0H1.771C.792 0 0 .774 0 1.729v20.542C0 23.227.792 24 1.771 24h20.451C23.2 24 24 23.227 24 22.271V1.729C24 .774 23.2 0 22.222 0h.003z"/>
          </svg>
          Sign in with LinkedIn
        </button>
        <p class="mt-4 text-sm text-gray-600">
          Don't have an account? <a routerLink="/register" class="text-blue-600 hover:underline">Register</a>
        </p>
      </div>
    </div>
  `
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
      next: () => this.router.navigate(['/dashboard']),
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
