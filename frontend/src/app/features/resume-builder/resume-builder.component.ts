import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { ResumeStateService } from './services/resume-state.service';
import { ResumeEditorComponent } from './editor/resume-editor.component';
import { ResumePreviewComponent } from './preview/resume-preview.component';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { NotifyService } from '../../shared/services/notify.service';

@Component({
  selector: 'app-resume-builder',
  imports: [TranslateModule, ResumeEditorComponent, ResumePreviewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './resume-builder.component.html',
})
export class ResumeBuilderComponent implements OnInit {
  protected state     = inject(ResumeStateService);
  private route       = inject(ActivatedRoute);
  private router      = inject(Router);
  private sectionsApi = inject(ProfileSectionsApiService);
  private privateApi  = inject(ProfilePrivateApiService);
  private jobsApi     = inject(JobsApiService);
  private notify      = inject(NotifyService);

  activeTab: 'editor' | 'preview' = 'editor';
  wizardJobId = signal<string | null>(null);

  ngOnInit(): void {
    const draftId = this.route.snapshot.paramMap.get('draftId');
    if (draftId) {
      this.state.loadDraft(draftId).subscribe({
        next: () => this.loadDraftJob(),
        error: this.notify.onError('notify.draftLoadFailed'),
      });
    } else {
      this.prefillFromProfile();
    }

    const wizardJobId = this.route.snapshot.queryParamMap.get('wizardJobId');
    if (wizardJobId) {
      this.wizardJobId.set(wizardJobId);
    }
  }

  /** Tailored drafts target a job; its description powers job-aware AI refinements. */
  private loadDraftJob(): void {
    const jobId = this.state.draftJobId();
    if (!jobId) return;
    this.jobsApi.getById(jobId).subscribe({
      next: job => this.state.jobDescription.set(job.descriptionClean ?? null),
      error: this.notify.onError('notify.loadFailed'),
    });
  }

  /** Fresh visit: prefill from the profile using two dedicated endpoints. */
  private prefillFromProfile(): void {
    forkJoin({
      full:    this.sectionsApi.getFullProfile(),
      private: this.privateApi.getPrivateInfo(),
    }).subscribe({
      next: ({ full, private: privateInfo }) => this.state.prefillFromProfile({
        profile:        full.profile,
        privateInfo,
        experience:     full.experience,
        education:      full.education,
        projects:       full.projects,
        certifications: full.certifications,
        languages:      full.languages,
        socials:        full.socials,
        strengths:      full.strengths,
      }),
      error: this.notify.onError('notify.loadFailed'),
    });
  }

  continueToWizard(): void {
    const jobId = this.wizardJobId();
    if (jobId) {
      this.router.navigate(['/apply'], { queryParams: { jobId } });
    }
  }
}
