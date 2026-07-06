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
  templateUrl: './job-add.component.html'
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
