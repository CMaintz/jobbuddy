import { Component, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';

/** Error code the backend sends when another account already holds the LinkedIn email. */
const ACCOUNT_EXISTS = 'ACCOUNT_EXISTS';

/**
 * Only refusals the user can act on get their own message. Any other failure, including a 409
 * from a sign-in collision, is worth simply retrying.
 */
function callbackErrorKey(e: HttpErrorResponse): string {
  if (e?.status === HttpStatusCode.Forbidden) return 'auth.error.linkedInEmailUnverified';
  if (e?.status === HttpStatusCode.Conflict && e.error?.code === ACCOUNT_EXISTS) {
    return 'auth.error.linkedInAccountExists';
  }
  return 'auth.error.linkedInFailed';
}

@Component({
  selector: 'app-linkedin-callback',
  imports: [RouterLink, TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './linkedin-callback.component.html'
})
export class LinkedInCallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  error = '';

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    if (!this.authService.consumeLinkedInState(params.get('state'))) {
      this.error = 'auth.error.linkedInState';
      return;
    }
    const code = params.get('code');
    if (!code) {
      this.error = 'auth.error.linkedInNoCode';
      return;
    }
    const redirectUri = window.location.origin + '/auth/linkedin/callback';
    this.authService.linkedinCallback(code, redirectUri).subscribe({
      next: (res) => this.router.navigate([res.onboardingComplete ? '/dashboard' : '/onboarding']),
      error: (e: HttpErrorResponse) => {
        this.error = callbackErrorKey(e);
      }
    });
  }
}
