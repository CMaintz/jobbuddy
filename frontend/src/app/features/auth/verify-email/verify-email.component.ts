import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';

/** Outcome of the last action, as the i18n key suffix under `auth.verifyEmail`. */
type VerifyStatus = '' | 'sent' | 'sendFailed' | 'stillUnverified' | 'checkFailed';

/**
 * Holding screen for a signed-in user whose email is not verified yet. The backend rejects
 * their token, so the app stays closed until Firebase reports the address as verified.
 */
@Component({
  selector: 'app-verify-email',
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './verify-email.component.html'
})
export class VerifyEmailComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  readonly email = signal<string | null>(null);
  readonly status = signal<VerifyStatus>('');
  readonly busy = signal(false);

  async ngOnInit(): Promise<void> {
    await this.authService.whenAuthReady();
    if (!this.authService.isAuthenticated()) {
      await this.router.navigate(['/login']);
      return;
    }
    this.email.set(this.authService.currentEmail());
  }

  async resend(): Promise<void> {
    await this.run(async () => {
      await this.authService.resendVerification();
      return 'sent';
    }, 'sendFailed');
  }

  async continue(): Promise<void> {
    await this.run(async () => {
      if (!(await this.authService.confirmEmailVerified())) return 'stillUnverified';
      const me = await firstValueFrom(this.authService.loadCurrentUser());
      await this.router.navigate([me.onboardingComplete ? '/dashboard' : '/onboarding']);
      return '';
    }, 'checkFailed');
  }

  async signOut(): Promise<void> {
    await this.authService.logout();
    await this.router.navigate(['/login']);
  }

  private async run(action: () => Promise<VerifyStatus>, onError: VerifyStatus): Promise<void> {
    this.busy.set(true);
    this.status.set('');
    try {
      this.status.set(await action());
    } catch {
      this.status.set(onError);
    } finally {
      this.busy.set(false);
    }
  }
}
