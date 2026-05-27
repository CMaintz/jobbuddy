import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ResumeStateService } from './services/resume-state.service';
import { ResumeEditorComponent } from './editor/resume-editor.component';
import { ResumePreviewComponent } from './preview/resume-preview.component';
import { ProfileSectionsApiService } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';

@Component({
  selector: 'app-resume-builder',
  standalone: true,
  imports: [CommonModule, ResumeEditorComponent, ResumePreviewComponent],
  template: `
    <div class="h-screen flex flex-col bg-gray-50">
      <!-- Wizard context banner -->
      @if (wizardJobId()) {
        <div class="bg-blue-600 text-white px-4 py-2 flex items-center justify-between text-sm">
          <span>Step 2 of 4 — Review and edit your tailored CV</span>
          <button
            class="bg-white text-blue-700 font-semibold px-4 py-1 rounded-md hover:bg-blue-50 transition-colors"
            (click)="continueToWizard()"
          >Continue to Cover Letter &rarr;</button>
        </div>
      }
      <!-- Header -->
      <header class="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200 shadow-sm">
        <div class="flex items-center gap-3">
          <h1 class="text-lg font-semibold text-gray-800">Resume Builder</h1>
          @if (state.isDirty()) {
            <span class="text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
              Unsaved changes
            </span>
          }
          @if (state.isSaving()) {
            <span class="text-xs text-blue-600 bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200">
              Saving...
            </span>
          }
        </div>
        <!-- Mobile tabs -->
        <div class="flex md:hidden rounded-lg border border-gray-300 overflow-hidden">
          <button
            class="px-3 py-1.5 text-sm font-medium transition-colors"
            [class.bg-blue-600]="activeTab === 'editor'"
            [class.text-white]="activeTab === 'editor'"
            [class.bg-white]="activeTab !== 'editor'"
            [class.text-gray-700]="activeTab !== 'editor'"
            (click)="activeTab = 'editor'"
          >Editor</button>
          <button
            class="px-3 py-1.5 text-sm font-medium transition-colors border-l border-gray-300"
            [class.bg-blue-600]="activeTab === 'preview'"
            [class.text-white]="activeTab === 'preview'"
            [class.bg-white]="activeTab !== 'preview'"
            [class.text-gray-700]="activeTab !== 'preview'"
            (click)="activeTab = 'preview'"
          >Preview</button>
        </div>
      </header>

      <!-- Split layout -->
      <div class="flex flex-1 overflow-hidden">
        <!-- Editor panel -->
        <div
          class="w-full md:w-[420px] md:min-w-[380px] overflow-y-auto bg-white border-r border-gray-200"
          [class.hidden]="activeTab !== 'editor'"
          [class.md:block]="true"
        >
          <app-resume-editor />
        </div>

        <!-- Preview panel -->
        <div
          class="flex-1 overflow-y-auto bg-gray-100 p-4"
          [class.hidden]="activeTab !== 'preview'"
          [class.md:block]="true"
        >
          <app-resume-preview />
        </div>
      </div>
    </div>
  `,
})
export class ResumeBuilderComponent implements OnInit {
  protected state     = inject(ResumeStateService);
  private route       = inject(ActivatedRoute);
  private router      = inject(Router);
  private sectionsApi = inject(ProfileSectionsApiService);
  private privateApi  = inject(ProfilePrivateApiService);

  activeTab: 'editor' | 'preview' = 'editor';
  wizardJobId = signal<string | null>(null);

  ngOnInit(): void {
    const draftId = this.route.snapshot.paramMap.get('draftId');
    if (draftId) {
      this.state.loadDraft(draftId).subscribe();
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
      this.router.navigate(['/apply', jobId], { queryParams: { step: 3 } });
    }
  }
}
