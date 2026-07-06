import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { FileUploadButtonComponent } from '../../shared/components/file-upload-button/file-upload-button.component';
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
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, FileUploadButtonComponent],
  templateUrl: './onboarding.component.html'
})
export class OnboardingComponent {
  private router = inject(Router);
  private http = inject(HttpClient);
  private profilePrivateApi = inject(ProfilePrivateApiService);
  private authService = inject(AuthService);

  currentStep = signal(0);
  saving = signal(false);
  error = signal('');
  cvUploading = signal(false);
  linkedinUploading = signal(false);

  parsedProfile: { skills?: string[]; headline?: string; experienceCount?: number } | null = null;

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

  onCvFileSelected(file: File): void {
    this.cvUploading.set(true);
    this.error.set('');
    const formData = new FormData();
    formData.append('file', file);
    this.http.post<any>('/api/v1/profile/import/cv-pdf', formData).subscribe({
      next: (profile) => {
        this.cvUploading.set(false);
        this.mergeParsedProfile(profile);
      },
      error: () => {
        this.cvUploading.set(false);
        this.error.set('CV import failed. Please try again or skip.');
      }
    });
  }

  onLinkedInFileSelected(file: File): void {
    this.linkedinUploading.set(true);
    this.error.set('');
    const formData = new FormData();
    formData.append('file', file);
    this.http.post<any>('/api/v1/users/me/import/linkedin-pdf', formData, { responseType: 'text' as 'json' }).subscribe({
      next: (raw) => {
        this.linkedinUploading.set(false);
        try {
          const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
          this.mergeParsedProfile(parsed);
        } catch {
          // ignore parse errors
        }
      },
      error: () => {
        this.linkedinUploading.set(false);
        this.error.set('LinkedIn import failed. Please try again or skip.');
      }
    });
  }

  private mergeParsedProfile(parsed: any): void {
    if (!parsed) return;
    const existing = this.parsedProfile ?? {};
    const newSkills = [...new Set([...(existing.skills ?? []), ...(parsed.skills ?? [])])];
    const expCount = Math.max(
      existing.experienceCount ?? 0,
      Array.isArray(parsed.experience) ? parsed.experience.length : 0
    );
    this.parsedProfile = {
      skills: newSkills,
      headline: existing.headline || parsed.headline || '',
      experienceCount: expCount,
    };
    if (!this.targetRoles && parsed.headline) {
      this.targetRoles = parsed.headline;
    }
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
        this.error.set('Failed to save profile. Please try again.');
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
        this.error.set('Failed to save preferences. Please try again.');
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
