import { Component, DestroyRef, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';

/** Outcome of the last action, as the i18n key suffix under `auth.verifyEmail`. */
type VerifyStatus = '' | 'sent' | 'sendFailed' | 'tooManyRequests' | 'stillUnverified' | 'checkFailed';

/** Firebase rate-limits verification mails; resending sooner only earns this error. */
const TOO_MANY_REQUESTS = 'auth/too-many-requests';
const RESEND_COOLDOWN_SECONDS = 60;
const ONE_SECOND_MS = 1000;

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
  private destroyRef = inject(DestroyRef);

  readonly email = signal<string | null>(null);
  readonly status = signal<VerifyStatus>('');
  readonly busy = signal(false);
  readonly cooldown = signal(0);

  async ngOnInit(): Promise<void> {
    await this.authService.whenAuthReady();
    if (!this.authService.isAuthenticated()) {
      await this.router.navigate(['/login']);
      return;
    }
    if (!this.authService.needsEmailVerification()) {
      await this.router.navigate(['/dashboard']);
      return;
    }
    this.email.set(this.authService.currentEmail());
  }

  async resend(): Promise<void> {
    if (this.cooldown() > 0) return;
    await this.run(async () => {
      try {
        await this.authService.resendVerification();
      } catch (e) {
        if ((e as { code?: string })?.code !== TOO_MANY_REQUESTS) throw e;
        this.startCooldown();
        return 'tooManyRequests';
      }
      this.startCooldown();
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

  private startCooldown(): void {
    this.cooldown.set(RESEND_COOLDOWN_SECONDS);
    const timer = setInterval(() => {
      this.cooldown.update(s => s - 1);
      if (this.cooldown() <= 0) clearInterval(timer);
    }, ONE_SECOND_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
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
