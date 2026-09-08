import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { ResumeStateService } from './services/resume-state.service';
import { ResumeEditorComponent } from './editor/resume-editor.component';
import { ResumePreviewComponent } from './preview/resume-preview.component';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { JobsApiService } from '../../core/api/jobs.api';

@Component({
  selector: 'app-resume-builder',
  standalone: true,
  imports: [CommonModule, TranslateModule, ResumeEditorComponent, ResumePreviewComponent],
  templateUrl: './resume-builder.component.html',
})
export class ResumeBuilderComponent implements OnInit {
  protected state     = inject(ResumeStateService);
  private route       = inject(ActivatedRoute);
  private router      = inject(Router);
  private sectionsApi = inject(ProfileSectionsApiService);
  private privateApi  = inject(ProfilePrivateApiService);
  private jobsApi     = inject(JobsApiService);

  activeTab: 'editor' | 'preview' = 'editor';
  wizardJobId = signal<string | null>(null);

  ngOnInit(): void {
    const draftId = this.route.snapshot.paramMap.get('draftId');
    if (draftId) {
      this.state.loadDraft(draftId).subscribe(() => {
        // Tailored drafts target a job — fetch its description for job-aware AI refinements
        const jobId = this.state.draftJobId();
        if (jobId) {
          this.jobsApi.getById(jobId).subscribe({
            next: job => this.state.jobDescription.set(job.descriptionClean ?? null),
            error: () => {}
          });
        }
      });
    } else {
      // Fresh visit — prefill from profile using two dedicated endpoints
      forkJoin({
        full:    this.sectionsApi.getFullProfile(),
        private: this.privateApi.getPrivateInfo(),
      }).subscribe(({ full, private: privateInfo }) => {
        this.state.prefillFromProfile({
          profile:        full.profile,
          privateInfo,
          experience:     full.experience,
          education:      full.education,
          projects:       full.projects,
          certifications: full.certifications,
          languages:      full.languages,
          socials:        full.socials,
          strengths:      full.strengths,
        });
      });
    }

    const wizardJobId = this.route.snapshot.queryParamMap.get('wizardJobId');
    if (wizardJobId) {
      this.wizardJobId.set(wizardJobId);
    }
  }

  continueToWizard(): void {
    const jobId = this.wizardJobId();
    if (jobId) {
      this.router.navigate(['/apply'], { queryParams: { jobId } });
    }
  }
}
