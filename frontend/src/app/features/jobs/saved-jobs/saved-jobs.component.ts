import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JobsApiService } from '../../../core/api/jobs.api';
import { Job } from '../../../core/models/job.model';
import { EmptyStateComponent } from '../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../shared/utils/async-ui';

@Component({
  selector: 'app-saved-jobs',
  standalone: true,
  imports: [CommonModule, RouterLink, EmptyStateComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Saved Jobs</h1>
        <a routerLink="/jobs/search" class="btn-secondary text-sm">Browse more jobs</a>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading saved jobs...</div>
      } @else if (jobs.length === 0) {
        <div class="card">
          <app-empty-state message="No saved jobs yet."></app-empty-state>
          <a routerLink="/jobs/search" class="btn-primary">Browse Jobs</a>
        </div>
      } @else {
        <div class="space-y-3">
          @for (job of jobs; track job.id) {
            <div class="card hover:shadow-md transition-shadow">
              <div class="flex items-start justify-between gap-3">
                <div class="flex-1 min-w-0">
                  <a [routerLink]="['/jobs', job.id]"
                     class="text-base font-semibold text-blue-600 hover:underline block truncate">
                    {{ job.title }}
                  </a>
                  <div class="text-sm text-gray-500 mt-0.5">
                    {{ job.companyName }}
                    @if (job.location) { &bull; {{ job.location }} }
                    @if (job.remoteType) { &bull; {{ job.remoteType | lowercase }} }
                  </div>
                  @if (job.technologies?.length) {
                    <div class="flex flex-wrap gap-1 mt-2">
                      @for (tech of job.technologies!.slice(0, 5); track tech) {
                        <span class="text-xs bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded">{{ tech }}</span>
                      }
                    </div>
                  }
                </div>
                <div class="flex flex-col gap-1.5 shrink-0 items-end">
                  @if (job.salaryMin) {
                    <span class="text-xs text-gray-600">{{ job.salaryMin | number:'1.0-0' }}+</span>
                  }
                  <a [routerLink]="['/ai/generate']" [queryParams]="{ jobId: job.id }"
                     class="text-xs text-blue-600 hover:underline">Generate →</a>
                  <button (click)="unsave(job)" class="text-xs text-red-400 hover:text-red-600">Remove</button>
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class SavedJobsComponent implements OnInit {
  private jobsApi = inject(JobsApiService);
  jobs: Job[] = [];
  loading = true;

  ngOnInit(): void {
    runAction({
      action$: this.jobsApi.getSaved(),
      setLoading: value => this.loading = value,
      next: jobs => this.jobs = jobs
    });
  }

  unsave(job: Job): void {
    this.jobsApi.unsave(job.id).subscribe(() => {
      this.jobs = this.jobs.filter(j => j.id !== job.id);
    });
  }
}
