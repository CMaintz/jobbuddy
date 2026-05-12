import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

type CheckState = 'idle' | 'checking' | 'found' | 'not-found';

@Component({
  selector: 'app-job-add',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">Add Job Manually</h1>

      <!-- URL Check Step -->
      <div class="card space-y-4">
        <h2 class="text-lg font-semibold text-gray-900">Step 1 — Check if job is already tracked</h2>
        <div class="flex gap-2">
          <input
            type="url"
            [(value)]="urlInput"
            (input)="onUrlInput($event)"
            placeholder="https://example.com/jobs/123"
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

        @if (checkState === 'found' && foundJobId) {
          <div class="rounded-lg bg-green-50 border border-green-200 p-4 space-y-2">
            <p class="text-green-800 font-medium">This job is already in our database.</p>
            <div class="flex gap-2">
              <a [routerLink]="['/jobs', foundJobId]" class="btn-secondary text-sm">View Job</a>
              <a [routerLink]="['/jobs', foundJobId]" [queryParams]="{ save: '1' }" class="btn-primary text-sm">Save this job</a>
            </div>
          </div>
        }

        @if (checkState === 'not-found') {
          <p class="text-sm text-gray-600">Job not found in database — please fill in the details below.</p>
        }
      </div>

      <!-- Manual Entry Form (shown after 404 check) -->
      @if (checkState === 'not-found') {
        <div class="card space-y-5">
          <h2 class="text-lg font-semibold text-gray-900">Step 2 — Enter job details</h2>

          <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">

            @if (submitError) {
              <div class="rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700">
                {{ submitError }}
              </div>
            }

            <div>
              <label class="label" for="title">Job Title <span class="text-red-500">*</span></label>
              <input id="title" type="text" formControlName="title" class="input" placeholder="e.g. Senior Software Engineer" />
              @if (form.get('title')?.invalid && form.get('title')?.touched) {
                <p class="text-red-500 text-xs mt-1">Job title is required.</p>
              }
            </div>

            <div>
              <label class="label" for="companyName">Company Name <span class="text-red-500">*</span></label>
              <input id="companyName" type="text" formControlName="companyName" class="input" placeholder="e.g. Acme Corp" />
              @if (form.get('companyName')?.invalid && form.get('companyName')?.touched) {
                <p class="text-red-500 text-xs mt-1">Company name is required.</p>
              }
            </div>

            <div>
              <label class="label" for="url">Job URL</label>
              <input id="url" type="url" formControlName="url" class="input" placeholder="https://example.com/jobs/123" />
            </div>

            <div>
              <label class="label" for="location">Location</label>
              <input id="location" type="text" formControlName="location" class="input" placeholder="e.g. London, UK" />
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

            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="label" for="salaryMin">Salary Min</label>
                <input id="salaryMin" type="number" formControlName="salaryMin" class="input" placeholder="e.g. 50000" />
              </div>
              <div>
                <label class="label" for="salaryMax">Salary Max</label>
                <input id="salaryMax" type="number" formControlName="salaryMax" class="input" placeholder="e.g. 80000" />
              </div>
            </div>

            <div>
              <label class="label" for="description">Description</label>
              <textarea id="description" formControlName="description" rows="6" class="input resize-y" placeholder="Paste the job description here…"></textarea>
            </div>

            <div class="flex gap-3 pt-2">
              <button type="submit" [disabled]="form.invalid || submitting" class="btn-primary">
                @if (submitting) { Saving… } @else { Save Job }
              </button>
              <a routerLink="/jobs" class="btn-secondary">Cancel</a>
            </div>
          </form>
        </div>
      }
    </div>
  `
})
export class JobAddComponent {
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private router = inject(Router);

  urlInput = '';
  checkState: CheckState = 'idle';
  foundJobId: string | null = null;

  submitting = false;
  submitError: string | null = null;

  form = this.fb.group({
    title: ['', Validators.required],
    companyName: ['', Validators.required],
    url: [''],
    location: [''],
    remoteType: [''],
    employmentType: [''],
    salaryMin: [null as number | null],
    salaryMax: [null as number | null],
    description: ['']
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
      error: (err) => {
        if (err.status === 404) {
          this.checkState = 'not-found';
          this.form.patchValue({ url: this.urlInput });
        } else {
          this.checkState = 'not-found';
          this.form.patchValue({ url: this.urlInput });
        }
      }
    });
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
      title: raw.title,
      companyName: raw.companyName,
      url: raw.url || null,
      location: raw.location || null,
      remoteType: raw.remoteType || null,
      employmentType: raw.employmentType || null,
      salaryMin: raw.salaryMin ?? null,
      salaryMax: raw.salaryMax ?? null,
      description: raw.description || null
    };

    this.http.post<{ id: string }>('/api/v1/jobs/manual', payload).subscribe({
      next: (job) => {
        this.router.navigate(['/jobs', job.id]);
      },
      error: (err) => {
        this.submitting = false;
        this.submitError = err?.error?.message ?? 'Failed to save job. Please try again.';
      }
    });
  }
}
