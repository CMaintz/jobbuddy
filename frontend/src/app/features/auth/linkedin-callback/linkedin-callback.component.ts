import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-linkedin-callback',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './linkedin-callback.component.html'
})
export class LinkedInCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  error = '';

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');
    if (!code) {
      this.error = 'Authorization failed. No code received from LinkedIn.';
      return;
    }
    const redirectUri = window.location.origin + '/auth/linkedin/callback';
    this.authService.linkedinCallback(code, redirectUri).subscribe({
      next: (res) => this.router.navigate([res.onboardingComplete ? '/dashboard' : '/onboarding']),
      error: () => {
        this.error = 'Sign in with LinkedIn failed. Please try again.';
      }
    });
  }
}
