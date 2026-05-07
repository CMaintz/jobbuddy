import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { JobsApiService } from '../../../core/api/jobs.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6 max-w-3xl mx-auto">
      <a routerLink="/jobs/search" class="text-sm text-blue-600 hover:underline">← Back to jobs</a>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (!job) {
        <div class="text-center py-12 text-gray-500">Job not found.</div>
      } @else {
        <div class="card space-y-4">
          <div class="flex items-start justify-between">
            <div>
              <h1 class="text-2xl font-bold text-gray-900">{{ job.title }}</h1>
              <p class="text-gray-600 mt-1">{{ job.companyName }}</p>
            </div>
            <div class="flex gap-2">
              <button (click)="saveJob()" class="btn-secondary text-sm">Save</button>
              <button (click)="apply()" [disabled]="applying" class="btn-primary text-sm">
                {{ applying ? 'Applying...' : 'Apply' }}
              </button>
            </div>
          </div>

          <!-- Meta -->
          <div class="flex flex-wrap gap-3 text-sm text-gray-500">
            @if (job.location) {
              <span>📍 {{ job.location }}</span>
            }
            @if (job.employmentType) {
              <span>🕐 {{ job.employmentType }}</span>
            }
            @if (job.remoteType) {
              <span>🏠 {{ job.remoteType }}</span>
            }
            @if (job.seniority) {
              <span>📊 {{ job.seniority }}</span>
            }
            @if (job.salaryMin) {
              <span>💰 {{ job.salaryMin | number:'1.0-0' }}
                @if (job.salaryMax) { – {{ job.salaryMax | number:'1.0-0' }} }
                {{ job.currency ?? 'DKK' }}/yr
              </span>
            }
          </div>

          @if (applied) {
            <div class="bg-green-50 text-green-700 rounded-md p-3 text-sm">
              Application created! <a routerLink="/applications" class="underline">View applications →</a>
            </div>
          }

          @if (job.aiSummary) {
            <div class="bg-blue-50 rounded-lg p-4">
              <h3 class="text-sm font-semibold text-blue-800 mb-1">AI Summary</h3>
              <p class="text-sm text-blue-900">{{ job.aiSummary }}</p>
            </div>
          }

          @if (job.technologies?.length || job.skills?.length) {
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Technologies & Skills</h3>
              <div class="flex flex-wrap gap-1.5">
                @for (t of (job.technologies ?? []).concat(job.skills ?? []); track t) {
                  <span class="text-xs bg-gray-100 text-gray-700 px-2 py-0.5 rounded-full">{{ t }}</span>
                }
              </div>
            </div>
          }

          @if (job.descriptionClean) {
            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Job Description</h3>
              <div class="text-sm text-gray-600 whitespace-pre-line leading-relaxed">{{ job.descriptionClean }}</div>
            </div>
          }

          <div class="pt-2 border-t border-gray-100">
            <a [href]="job.url" target="_blank" rel="noopener"
               class="text-sm text-blue-600 hover:underline">
              View original posting →
            </a>
          </div>
        </div>
      }
    </div>
  `
})
export class JobDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private jobsApi = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);

  job: Job | null = null;
  loading = true;
  applying = false;
  applied = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.jobsApi.getById(id).subscribe({
      next: j => { this.job = j; this.loading = false; },
      error: () => this.loading = false
    });
  }

  saveJob(): void {
    if (this.job) this.jobsApi.save(this.job.id).subscribe();
  }

  apply(): void {
    if (!this.job || this.applying) return;
    this.applying = true;
    this.appsApi.create(this.job.id).subscribe({
      next: () => { this.applying = false; this.applied = true; },
      error: () => this.applying = false
    });
  }
}
