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
        <p class="mt-4 text-sm text-gray-600">
          Don't have an account? <a routerLink="/register" class="text-blue-600 hover:underline">Register</a>
        </p>
      </div>
    </div>
  `
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
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
    this.auth.login(email!, password!).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => { this.error = e.error?.message || 'Login failed'; this.loading = false; }
    });
  }
}
