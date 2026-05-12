import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-linkedin-callback',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen flex items-center justify-center">
      <div class="text-center">
        @if (error) {
          <div class="bg-red-50 text-red-700 rounded-md p-4 text-sm">{{ error }}</div>
          <a routerLink="/login" class="mt-4 text-blue-600 hover:underline text-sm block">Back to login</a>
        } @else {
          <div class="text-gray-600">Completing sign in...</div>
        }
      </div>
    </div>
  `
})
export class LinkedInCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private auth = inject(AuthService);

  error = '';

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (!code) {
      this.error = 'Authorization failed. No code received from LinkedIn.';
      return;
    }
    const redirectUri = window.location.origin + '/auth/linkedin/callback';
    this.auth.linkedinCallback(code, redirectUri).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: () => {
        this.error = 'Sign in with LinkedIn failed. Please try again.';
      }
    });
  }
}
