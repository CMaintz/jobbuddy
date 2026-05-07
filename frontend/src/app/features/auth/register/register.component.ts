import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="min-h-screen flex items-center justify-center">
      <div class="card w-full max-w-md">
        <h1 class="text-2xl font-bold text-gray-900 mb-6">Create your account</h1>
        @if (error) {
          <div class="bg-red-50 text-red-700 rounded-md p-3 mb-4 text-sm">{{ error }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label">Full Name</label>
            <input type="text" formControlName="fullName" class="input" placeholder="Jane Doe" />
          </div>
          <div>
            <label class="label">Email</label>
            <input type="email" formControlName="email" class="input" placeholder="you@example.com" />
          </div>
          <div>
            <label class="label">Password</label>
            <input type="password" formControlName="password" class="input" placeholder="Min 8 characters" />
          </div>
          <button type="submit" [disabled]="form.invalid || loading" class="btn-primary w-full">
            {{ loading ? 'Creating account...' : 'Create account' }}
          </button>
        </form>
        <p class="mt-4 text-sm text-gray-600">
          Already have an account? <a routerLink="/login" class="text-blue-600 hover:underline">Sign in</a>
        </p>
      </div>
    </div>
  `
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  form = this.fb.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  error = '';
  loading = false;

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const { email, password, fullName } = this.form.value;
    this.auth.register(email!, password!, fullName!).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => { this.error = e.error?.message || 'Registration failed'; this.loading = false; }
    });
  }
}
