import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { CvImportPanelComponent, ImportPreview } from '../../shared/components/cv-import-panel/cv-import-panel.component';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { AuthService } from '../../core/auth/auth.service';

interface Step {
  key: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, CvImportPanelComponent],
  templateUrl: './onboarding.component.html'
})
export class OnboardingComponent {
  private router = inject(Router);
  private http = inject(HttpClient);
  private profilePrivateApi = inject(ProfilePrivateApiService);
  private authService = inject(AuthService);
  private translate = inject(TranslateService);

  currentStep = signal(0);
  saving = signal(false);
  error = signal('');

  /** One-line confirmation after the shared import panel merged data into the master CV. */
  importedSummary = '';

  steps: Step[] = [
    { key: 'welcome', label: 'Welcome', icon: 'sparkle' },
    { key: 'profile', label: 'Profile', icon: 'user' },
    { key: 'cv', label: 'CV Import', icon: 'upload' },
    { key: 'prefs', label: 'Preferences', icon: 'settings' },
    { key: 'goal', label: 'Goal', icon: 'target' },
    { key: 'done', label: 'Done', icon: 'check' },
  ];

  name = '';
  title = '';
  location = '';
  targetRoles = '';
  locations = '';
  workType = signal('Remote');
  workTypes = ['Remote', 'Hybrid', 'On-site', 'Any'];
  weeklyGoal = 5;

  next(): void {
    this.error.set('');
    const step = this.currentStep();

    if (step === 1) {
      this.saveProfileStep();
    } else if (step === 3) {
      this.savePreferencesStep();
    } else if (step === 4) {
      this.saveGoalAndComplete();
    } else {
      this.advance();
    }
  }

  prev(): void {
    if (this.currentStep() > 0) {
      this.currentStep.set(this.currentStep() - 1);
    }
  }

  skipCvImport(): void {
    this.advance();
  }

  goTo(path: string): void {
    this.router.navigate([path]);
  }

  /** The shared panel already merged the data server-side — reflect it in the wizard. */
  onImportApplied(preview: ImportPreview): void {
    this.error.set('');
    const bits: string[] = [];
    if (preview.headline) bits.push(this.translate.instant('onboarding.import.headline', { value: preview.headline }));
    if (preview.skills.length) bits.push(this.translate.instant('onboarding.import.skills', { n: preview.skills.length }));
    this.importedSummary = bits.length
      ? this.translate.instant('onboarding.import.summary', { items: bits.join(', ') })
      : this.translate.instant('onboarding.import.applied');
    if (!this.targetRoles && preview.headline) this.targetRoles = preview.headline;
    if (!this.title && preview.headline) this.title = preview.headline;
    if (!this.name && preview.fullName) this.name = preview.fullName;
  }

  private saveProfileStep(): void {
    this.saving.set(true);
    const privateUpdate = this.profilePrivateApi.updatePrivateInfo({
      fullName: this.name,
      location: this.location,
    });
    const profileUpdate = this.http.put('/api/v1/users/me/profile', {
      headline: this.title,
    });
    forkJoin([privateUpdate, profileUpdate]).subscribe({
      next: () => {
        this.saving.set(false);
        this.advance();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('onboarding.error.profile');
      }
    });
  }

  private savePreferencesStep(): void {
    this.saving.set(true);
    const locationList = this.locations
      .split(',')
      .map(l => l.trim())
      .filter(l => l.length > 0);
    const roleList = this.targetRoles
      .split(',')
      .map(r => r.trim())
      .filter(r => r.length > 0);
    this.http.put('/api/v1/users/me/preferences', {
      preferredRemoteTypes: [this.workType()],
      preferredLocations: locationList,
      positiveSignals: roleList,
    }).subscribe({
      next: () => {
        this.saving.set(false);
        this.advance();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('onboarding.error.preferences');
      }
    });
  }

  private saveGoalAndComplete(): void {
    this.saving.set(true);
    this.http.put('/api/v1/users/me/preferences', {
      weeklyApplicationGoal: this.weeklyGoal,
    }).subscribe({
      next: () => {
        this.authService.completeOnboarding().subscribe({
          next: () => { this.saving.set(false); this.advance(); },
          error: () => { this.saving.set(false); this.advance(); }
        });
      },
      error: () => {
        // Preferences save failed — still mark onboarding complete and advance
        this.authService.completeOnboarding().subscribe({
          next: () => { this.saving.set(false); this.advance(); },
          error: () => { this.saving.set(false); this.advance(); }
        });
      }
    });
  }

  private advance(): void {
    if (this.currentStep() < this.steps.length - 1) {
      this.currentStep.set(this.currentStep() + 1);
    }
  }
}
