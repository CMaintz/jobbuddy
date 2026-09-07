import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApplyWizardStateService } from '../../apply/apply-wizard-state.service';

type CheckState = 'idle' | 'checking' | 'found' | 'not-found';

@Component({
  selector: 'app-job-add',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">Paste Job & Apply</h1>
      <p class="text-gray-500 -mt-4 text-sm">Paste any job description and let AI build a tailored CV + cover letter instantly.</p>

      <!-- URL Check Step -->
      <div class="card space-y-4">
        <h2 class="text-lg font-semibold text-gray-900">Step 1 — Check if job is already tracked</h2>
        <div class="flex gap-2">
          <input
            type="url"
            [(value)]="urlInput"
            (input)="onUrlInput($event)"
            placeholder="https://example.com/jobs/123 (optional)"
            class="input flex-1"
          />
          <button
            type="button"
            (click)="checkUrl()"
            [disabled]="checkState === 'checking' || !urlInput"
            class="btn-primary whitespace-nowrap"
          >
            @if (checkState === 'checking') {
              Checking…
            } @else {
              Check
            }
          </button>
        </div>
        <p class="text-xs text-gray-400">Skip this step if you don't have a URL — fill in the form below directly.</p>

        @if (checkState === 'found' && foundJobId) {
          <div class="rounded-lg bg-green-50 border border-green-200 p-4 space-y-2">
            <p class="text-green-800 font-medium">This job is already in our database.</p>
            <div class="flex gap-2 flex-wrap">
              <a [routerLink]="['/jobs', foundJobId]" class="btn-secondary text-sm">View Job</a>
              <button (click)="applyToExisting(foundJobId!)" class="btn-primary text-sm">Apply Now with AI</button>
            </div>
          </div>
        }

        @if (checkState === 'not-found') {
          <p class="text-sm text-gray-600">Not found — fill in the details below.</p>
        }
      </div>

      <!-- Manual Entry Form -->
      <div class="card space-y-5">
        <h2 class="text-lg font-semibold text-gray-900">Job Details</h2>

        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">

          @if (submitError) {
            <div class="rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700">
              {{ submitError }}
            </div>
          }

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label" for="title">Job Title <span class="text-red-500">*</span></label>
              <input id="title" type="text" formControlName="title" class="input" placeholder="e.g. Senior Software Engineer" />
              @if (form.get('title')?.invalid && form.get('title')?.touched) {
                <p class="text-red-500 text-xs mt-1">Job title is required.</p>
              }
            </div>

            <div>
              <label class="label" for="companyName">Company Name</label>
              <input id="companyName" type="text" formControlName="companyName" class="input" placeholder="e.g. Acme Corp" />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label" for="url">Job URL</label>
              <input id="url" type="url" formControlName="url" class="input" placeholder="https://example.com/jobs/123" />
            </div>

            <div>
              <label class="label" for="location">Location</label>
              <input id="location" type="text" formControlName="location" class="input" placeholder="e.g. London, UK" />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label" for="remoteType">Remote Type</label>
              <select id="remoteType" formControlName="remoteType" class="input">
                <option value="">— Select —</option>
                <option value="ON_SITE">On-site</option>
                <option value="HYBRID">Hybrid</option>
                <option value="FULLY_REMOTE">Fully Remote</option>
              </select>
            </div>

            <div>
              <label class="label" for="employmentType">Employment Type</label>
              <select id="employmentType" formControlName="employmentType" class="input">
                <option value="">— Select —</option>
                <option value="FULL_TIME">Full-time</option>
                <option value="PART_TIME">Part-time</option>
                <option value="CONTRACT">Contract</option>
                <option value="FREELANCE">Freelance</option>
              </select>
            </div>
          </div>

          <div>
            <label class="label" for="description">Job Description <span class="text-red-500">*</span></label>
            <textarea id="description" formControlName="description" rows="10" class="input resize-y"
              placeholder="Paste the full job description here. The AI uses this to tailor your CV and cover letter."></textarea>
            @if (form.get('description')?.invalid && form.get('description')?.touched) {
              <p class="text-red-500 text-xs mt-1">Job description is required for AI generation.</p>
            }
          </div>

          <div class="flex gap-3 pt-2">
            <!-- Primary: Apply Now with AI -->
            <button
              type="button"
              [disabled]="form.invalid || submitting"
              (click)="submitAndApply()"
              class="btn-primary flex-1"
            >
              @if (submitting && applyMode) { Saving… } @else { Apply Now with AI }
            </button>

            <!-- Secondary: Just save -->
            <button
              type="button"
              [disabled]="form.invalid || submitting"
              (click)="submitAndSave()"
              class="btn-secondary"
            >
              @if (submitting && !applyMode) { Saving… } @else { Save Job Only }
            </button>

            <a routerLink="/jobs" class="btn-secondary">Cancel</a>
          </div>
        </form>
      </div>
    </div>
  `
})
export class JobAddComponent {
  private fb           = inject(FormBuilder);
  private http         = inject(HttpClient);
  private router       = inject(Router);
  private wizardState  = inject(ApplyWizardStateService);

  urlInput = '';
  checkState: CheckState = 'idle';
  foundJobId: string | null = null;

  submitting = false;
  submitError: string | null = null;
  applyMode = false;

  form = this.fb.group({
    title:          ['', Validators.required],
    companyName:    [''],
    url:            [''],
    location:       [''],
    remoteType:     [''],
    employmentType: [''],
    salaryMin:      [null as number | null],
    salaryMax:      [null as number | null],
    description:    ['', Validators.required],
  });

  onUrlInput(event: Event): void {
    this.urlInput = (event.target as HTMLInputElement).value;
  }

  checkUrl(): void {
    if (!this.urlInput) return;
    this.checkState = 'checking';
    this.foundJobId = null;

    this.http.get<{ id: string }>(`/api/v1/jobs/lookup?url=${encodeURIComponent(this.urlInput)}`).subscribe({
      next: (job) => {
        this.foundJobId = job.id;
        this.checkState = 'found';
      },
      error: () => {
        this.checkState = 'not-found';
        this.form.patchValue({ url: this.urlInput });
      }
    });
  }

  /** Immediately navigate to wizard for a job already in the database. */
  applyToExisting(jobId: string): void {
    this.wizardState.clear();
    this.router.navigate(['/apply', jobId]);
  }

  submitAndApply(): void {
    this.applyMode = true;
    this.submit();
  }

  submitAndSave(): void {
    this.applyMode = false;
    this.submit();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.submitError = null;

    const raw = this.form.value;
    const payload: Record<string, unknown> = {
      title:          raw.title,
      companyName:    raw.companyName   || null,
      url:            raw.url           || null,
      location:       raw.location      || null,
      remoteType:     raw.remoteType    || null,
      employmentType: raw.employmentType || null,
      salaryMin:      raw.salaryMin     ?? null,
      salaryMax:      raw.salaryMax     ?? null,
      description:    raw.description   || null,
    };

    this.http.post<{ id: string }>('/api/v1/jobs/manual', payload).subscribe({
      next: (job) => {
        if (this.applyMode) {
          this.wizardState.clear();
          this.router.navigate(['/apply', job.id]);
        } else {
          this.router.navigate(['/jobs', job.id]);
        }
      },
      error: (err) => {
        this.submitting = false;
        this.submitError = err?.error?.message ?? 'Failed to save job. Please try again.';
      }
    });
  }
}
