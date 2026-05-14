import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { JobsApiService } from '../../../core/api/jobs.api';
import { Job } from '../../../core/models/job.model';

const FILTERS_KEY = 'aa_job_filters';

interface JobFilters {
  remoteTypes: string[];
  employmentTypes: string[];
  seniorities: string[];
  salaryMin: number | null;
  salaryMax: number | null;
  technologies: string;
}

const DEFAULT_FILTERS: JobFilters = {
  remoteTypes: [], employmentTypes: [], seniorities: [],
  salaryMin: null, salaryMax: null, technologies: ''
};

@Component({
  selector: 'app-jobs-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Browse Jobs</h1>
        <div class="flex items-center gap-3">
          <button (click)="showFilters = !showFilters"
                  class="btn-secondary text-sm flex items-center gap-1">
            <span>Filters</span>
            @if (activeFilterCount > 0) {
              <span class="bg-blue-600 text-white text-xs rounded-full w-4 h-4 flex items-center justify-center">
                {{ activeFilterCount }}
              </span>
            }
          </button>
          <span class="text-sm text-gray-500">{{ total }} jobs found</span>
        </div>
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

      <!-- Filter panel -->
      @if (showFilters) {
        <div class="card space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="font-semibold text-gray-900 text-sm">Filters</h2>
            <button (click)="clearFilters()" class="text-xs text-blue-600 hover:underline">Clear all</button>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            <!-- Remote type -->
            <div>
              <label class="label">Remote</label>
              <div class="space-y-1">
                @for (opt of remoteOptions; track opt.value) {
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" [checked]="filters.remoteTypes.includes(opt.value)"
                           (change)="toggleFilter('remoteTypes', opt.value)"
                           class="rounded border-gray-300" />
                    <span class="text-sm text-gray-700">{{ opt.label }}</span>
                  </label>
                }
              </div>
            </div>

            <!-- Employment type -->
            <div>
              <label class="label">Employment</label>
              <div class="space-y-1">
                @for (opt of employmentOptions; track opt.value) {
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" [checked]="filters.employmentTypes.includes(opt.value)"
                           (change)="toggleFilter('employmentTypes', opt.value)"
                           class="rounded border-gray-300" />
                    <span class="text-sm text-gray-700">{{ opt.label }}</span>
                  </label>
                }
              </div>
            </div>

            <!-- Seniority -->
            <div>
              <label class="label">Seniority</label>
              <div class="space-y-1">
                @for (opt of seniorityOptions; track opt.value) {
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" [checked]="filters.seniorities.includes(opt.value)"
                           (change)="toggleFilter('seniorities', opt.value)"
                           class="rounded border-gray-300" />
                    <span class="text-sm text-gray-700">{{ opt.label }}</span>
                  </label>
                }
              </div>
            </div>

            <!-- Salary range -->
            <div class="sm:col-span-2 lg:col-span-2">
              <label class="label">Salary (DKK/yr)</label>
              <div class="flex items-center gap-2">
                <input type="number" [(ngModel)]="filters.salaryMin" (change)="onFilterChange()"
                       placeholder="Min" class="input text-sm" style="width: 110px;" />
                <span class="text-gray-400 text-sm">–</span>
                <input type="number" [(ngModel)]="filters.salaryMax" (change)="onFilterChange()"
                       placeholder="Max" class="input text-sm" style="width: 110px;" />
              </div>
            </div>

            <!-- Technologies -->
            <div>
              <label class="label">Technology</label>
              <input type="text" [(ngModel)]="filters.technologies" (input)="onFilterChange()"
                     placeholder="e.g. React, Java" class="input text-sm" />
            </div>
          </div>
        </div>
      }

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading jobs...</div>
      } @else if (filteredJobs.length === 0) {
        <div class="text-center py-12 text-gray-500">No jobs found.</div>
      } @else {
        <div class="space-y-3">
          @for (job of filteredJobs; track job.id) {
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
                    @if (job.seniority) {
                      &bull; <span class="text-gray-400">{{ job.seniority }}</span>
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
  private http = inject(HttpClient);

  searchCtrl = new FormControl('');
  jobs: Job[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  showFilters = false;

  filters: JobFilters = { ...DEFAULT_FILTERS };

  remoteOptions = [
    { value: 'FULLY_REMOTE', label: 'Fully remote' },
    { value: 'HYBRID', label: 'Hybrid' },
    { value: 'ON_SITE', label: 'On-site' }
  ];
  employmentOptions = [
    { value: 'FULL_TIME', label: 'Full-time' },
    { value: 'PART_TIME', label: 'Part-time' },
    { value: 'CONTRACT', label: 'Contract' },
    { value: 'FREELANCE', label: 'Freelance' }
  ];
  seniorityOptions = [
    { value: 'JUNIOR', label: 'Junior' },
    { value: 'MID', label: 'Mid' },
    { value: 'SENIOR', label: 'Senior' },
    { value: 'LEAD', label: 'Lead' },
    { value: 'PRINCIPAL', label: 'Principal' }
  ];

  get filteredJobs(): Job[] {
    return this.jobs.filter(j => {
      if (this.filters.remoteTypes.length && j.remoteType && !this.filters.remoteTypes.includes(j.remoteType)) return false;
      if (this.filters.employmentTypes.length && j.employmentType && !this.filters.employmentTypes.includes(j.employmentType)) return false;
      if (this.filters.seniorities.length && j.seniority && !this.filters.seniorities.includes(j.seniority)) return false;
      if (this.filters.salaryMin && j.salaryMax && j.salaryMax < this.filters.salaryMin) return false;
      if (this.filters.salaryMax && j.salaryMin && j.salaryMin > this.filters.salaryMax) return false;
      if (this.filters.technologies) {
        const techs = this.filters.technologies.toLowerCase().split(',').map(t => t.trim()).filter(Boolean);
        const jobTechs = (j.technologies ?? []).map(t => t.toLowerCase());
        if (techs.length && !techs.some(t => jobTechs.some(jt => jt.includes(t)))) return false;
      }
      return true;
    });
  }

  get activeFilterCount(): number {
    return this.filters.remoteTypes.length + this.filters.employmentTypes.length +
           this.filters.seniorities.length +
           (this.filters.salaryMin ? 1 : 0) + (this.filters.salaryMax ? 1 : 0) +
           (this.filters.technologies ? 1 : 0);
  }

  ngOnInit(): void {
    const saved = localStorage.getItem(FILTERS_KEY);
    if (saved) {
      try { this.filters = { ...DEFAULT_FILTERS, ...JSON.parse(saved) }; } catch {}
    } else {
      // Pre-populate from user preferences if no saved filters
      this.http.get<any>('/api/v1/users/me/preferences').subscribe({
        next: prefs => {
          if (!localStorage.getItem(FILTERS_KEY)) {
            this.filters = {
              ...DEFAULT_FILTERS,
              remoteTypes: prefs.preferredRemoteTypes ?? [],
              employmentTypes: prefs.preferredEmploymentTypes ?? [],
              seniorities: prefs.preferredSeniority ?? [],
              salaryMin: prefs.salaryMin ?? null,
              salaryMax: prefs.salaryMax ?? null
            };
            if (this.activeFilterCount > 0) this.showFilters = true;
          }
        }
      });
    }
    if (this.activeFilterCount > 0) this.showFilters = true;

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

  toggleFilter(key: 'remoteTypes' | 'employmentTypes' | 'seniorities', value: string): void {
    const arr = this.filters[key];
    const idx = arr.indexOf(value);
    if (idx >= 0) arr.splice(idx, 1);
    else arr.push(value);
    this.saveFilters();
  }

  onFilterChange(): void {
    this.saveFilters();
  }

  clearFilters(): void {
    this.filters = { ...DEFAULT_FILTERS };
    localStorage.removeItem(FILTERS_KEY);
  }

  private saveFilters(): void {
    localStorage.setItem(FILTERS_KEY, JSON.stringify(this.filters));
  }

  loadPage(p: number): void {
    this.page = p;
    const q = this.searchCtrl.value ?? '';
    this.loading = true;
    const obs: Observable<any> = q ? this.api.search(q, p, this.pageSize) : this.api.getJobs(p, this.pageSize);
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
