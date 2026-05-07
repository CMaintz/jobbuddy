import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { JobsApiService } from '../../../core/api/jobs.api';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-jobs-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Browse Jobs</h1>
        <span class="text-sm text-gray-500">{{ total }} jobs found</span>
      </div>

      <!-- Search bar -->
      <div class="card">
        <input
          type="text"
          [formControl]="searchCtrl"
          class="input"
          placeholder="Search by title, company, technology..."
        />
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading jobs...</div>
      } @else if (jobs.length === 0) {
        <div class="text-center py-12 text-gray-500">No jobs found.</div>
      } @else {
        <div class="space-y-3">
          @for (job of jobs; track job.id) {
            <div class="card hover:shadow-md transition-shadow">
              <div class="flex items-start justify-between">
                <div class="flex-1 min-w-0">
                  <a [routerLink]="['/jobs', job.id]"
                     class="text-lg font-semibold text-blue-600 hover:underline block truncate">
                    {{ job.title }}
                  </a>
                  <div class="text-sm text-gray-500 mt-0.5">
                    {{ job.companyName }}
                    @if (job.location) { &bull; {{ job.location }} }
                    @if (job.remoteType && job.remoteType !== 'ON_SITE') {
                      &bull; <span class="text-green-600">{{ remoteLabel(job.remoteType) }}</span>
                    }
                  </div>
                  @if (job.aiSummary) {
                    <p class="text-sm text-gray-600 mt-2 line-clamp-2">{{ job.aiSummary }}</p>
                  }
                  @if (job.technologies?.length) {
                    <div class="flex flex-wrap gap-1 mt-2">
                      @for (tech of job.technologies!.slice(0, 5); track tech) {
                        <span class="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full">{{ tech }}</span>
                      }
                    </div>
                  }
                </div>
                <div class="ml-4 flex flex-col items-end gap-2 shrink-0">
                  @if (job.salaryMin) {
                    <span class="text-sm font-medium text-gray-700">
                      {{ job.salaryMin | number:'1.0-0' }}{{ job.salaryMax ? '–' + (job.salaryMax | number:'1.0-0') : '+' }}
                      {{ job.currency ?? 'DKK' }}
                    </span>
                  }
                  <div class="flex gap-2">
                    <button (click)="saveJob(job.id, $event)"
                            class="text-xs text-gray-500 hover:text-blue-600 px-2 py-1 border border-gray-200 rounded">
                      Save
                    </button>
                    <button (click)="ignoreJob(job.id, $event)"
                            class="text-xs text-gray-500 hover:text-red-500 px-2 py-1 border border-gray-200 rounded">
                      Hide
                    </button>
                  </div>
                </div>
              </div>
            </div>
          }
        </div>

        <!-- Pagination -->
        @if (total > pageSize) {
          <div class="flex justify-center gap-3 pt-4">
            <button (click)="loadPage(page - 1)" [disabled]="page === 0"
                    class="btn-secondary disabled:opacity-40">Previous</button>
            <span class="self-center text-sm text-gray-600">Page {{ page + 1 }}</span>
            <button (click)="loadPage(page + 1)" [disabled]="(page + 1) * pageSize >= total"
                    class="btn-secondary disabled:opacity-40">Next</button>
          </div>
        }
      }
    </div>
  `
})
export class JobsListComponent implements OnInit {
  private api = inject(JobsApiService);

  searchCtrl = new FormControl('');
  jobs: Job[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;

  ngOnInit(): void {
    this.searchCtrl.valueChanges.pipe(
      startWith(''),
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading = true;
        this.page = 0;
        return q ? this.api.search(q, 0, this.pageSize) : this.api.getJobs(0, this.pageSize);
      })
    ).subscribe({
      next: (res: any) => {
        this.jobs = res.jobs ?? res.content ?? [];
        this.total = res.total ?? res.totalElements ?? 0;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  loadPage(p: number): void {
    this.page = p;
    const q = this.searchCtrl.value ?? '';
    this.loading = true;
    const obs = q ? this.api.search(q, p, this.pageSize) : this.api.getJobs(p, this.pageSize);
    obs.subscribe({
      next: (res: any) => {
        this.jobs = res.jobs ?? res.content ?? [];
        this.total = res.total ?? res.totalElements ?? 0;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  saveJob(id: string, e: Event): void {
    e.preventDefault();
    this.api.save(id).subscribe();
  }

  ignoreJob(id: string, e: Event): void {
    e.preventDefault();
    this.api.ignore(id).subscribe(() => {
      this.jobs = this.jobs.filter(j => j.id !== id);
    });
  }

  remoteLabel(type: string): string {
    return type === 'FULLY_REMOTE' ? 'Remote' : 'Hybrid';
  }
}
