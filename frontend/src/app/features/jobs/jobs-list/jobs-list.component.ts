import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Observable, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { JobsApiService } from '../../../core/api/jobs.api';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { Job } from '../../../core/models/job.model';
import { ApplicationStatus } from '../../../core/models/application.model';
import { EmptyStateComponent } from '../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../shared/utils/async-ui';
import {
  DEFAULT_JOB_FILTERS,
  EMPLOYMENT_OPTIONS,
  INDUSTRY_OPTIONS,
  JobFilters,
  JOB_FILTERS_KEY,
  REMOTE_OPTIONS,
  SENIORITY_OPTIONS
} from '../job-list.filters';

@Component({
  selector: 'app-jobs-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, FormsModule, EmptyStateComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Browse Jobs</h1>
        <div class="flex items-center gap-3">
          <!-- Hide applied quick-toggle -->
          <label class="flex items-center gap-2 cursor-pointer select-none">
            <input type="checkbox" [(ngModel)]="filters.hideApplied" (change)="onFilterChange()"
                   class="rounded border-gray-300 text-blue-600" />
            <span class="text-sm text-gray-600">Hide applied</span>
          </label>
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

            <!-- Industry -->
            <div>
              <label class="label">Industry</label>
              <div class="space-y-1">
                @for (opt of industryOptions; track opt.value) {
                  <label class="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" [checked]="filters.industries.includes(opt.value)"
                           (change)="toggleFilter('industries', opt.value)"
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

            <!-- Max commute distance -->
            <div>
              <label class="label">Max commute distance (km)</label>
              <input type="number" [(ngModel)]="maxCommuteKm" (ngModelChange)="onCommuteKmChange($event)"
                     min="0" max="300" step="10"
                     placeholder="e.g. 30" class="input text-sm" style="width: 110px;" />
              <p class="text-xs text-gray-400 mt-1">Affects your recommendations feed</p>
            </div>
          </div>
        </div>
      }

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading jobs...</div>
      } @else if (filteredJobs.length === 0) {
        <app-empty-state message="No jobs found."></app-empty-state>
      } @else {
        <div class="space-y-3">
          @for (job of filteredJobs; track job.id) {
            <div class="card hover:shadow-md transition-shadow"
                 [class.opacity-60]="isFullyDone(job.id)">
              <div class="flex items-start justify-between">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <a [routerLink]="['/jobs', job.id]"
                       class="text-lg font-semibold text-blue-600 hover:underline block truncate">
                      {{ job.title }}
                    </a>
                    @if (appBadge(job.id); as badge) {
                      <span class="text-xs font-medium px-2 py-0.5 rounded-full shrink-0"
                            [class]="badge.cls">{{ badge.label }}</span>
                    }
                    @if (job.duplicateGroupId && duplicateCount(job.duplicateGroupId) > 1) {
                      <span class="text-xs bg-amber-50 text-amber-700 border border-amber-200 px-2 py-0.5 rounded-full shrink-0"
                            title="Multiple similar postings found">
                        {{ duplicateCount(job.duplicateGroupId) }} similar
                      </span>
                    }
                  </div>
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
  private api     = inject(JobsApiService);
  private appsApi = inject(ApplicationsApiService);
  private http    = inject(HttpClient);

  searchCtrl = new FormControl('');
  jobs: Job[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  showFilters = false;

  /** maxCommuteKm is stored in preferences, not in localStorage filters */
  maxCommuteKm: number | null = null;
  private commuteKmSubject = new Subject<number | null>();

  /** jobId → latest application status */
  private appliedMap = new Map<string, ApplicationStatus>();

  filters: JobFilters = { ...DEFAULT_JOB_FILTERS };
  remoteOptions    = REMOTE_OPTIONS;
  employmentOptions = EMPLOYMENT_OPTIONS;
  seniorityOptions = SENIORITY_OPTIONS;
  industryOptions  = INDUSTRY_OPTIONS;

  get filteredJobs(): Job[] {
    return this.jobs.filter(j => {
      if (this.filters.hideApplied && this.appliedMap.has(j.id)) return false;
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
           this.filters.seniorities.length + this.filters.industries.length +
           (this.filters.salaryMin ? 1 : 0) + (this.filters.salaryMax ? 1 : 0) +
           (this.filters.technologies ? 1 : 0) +
           (this.filters.hideApplied ? 1 : 0);
  }

  appBadge(jobId: string): { label: string; cls: string } | null {
    const status = this.appliedMap.get(jobId);
    if (!status) return null;
    switch (status) {
      case 'SAVED':      return { label: 'Saved',       cls: 'bg-gray-100 text-gray-600' };
      case 'PREPARING':  return { label: 'In Progress',  cls: 'bg-blue-100 text-blue-700' };
      case 'APPLIED':    return { label: 'Applied',      cls: 'bg-green-100 text-green-700' };
      case 'RECRUITER_CONTACT':
      case 'INTERVIEW':
      case 'TECHNICAL_TEST':
      case 'FINAL_ROUND': return { label: 'Interviewing', cls: 'bg-purple-100 text-purple-700' };
      case 'OFFER':      return { label: 'Offer!',       cls: 'bg-yellow-100 text-yellow-700' };
      case 'REJECTED':   return { label: 'Rejected',     cls: 'bg-red-100 text-red-600' };
      case 'ARCHIVED':   return null;
      default:           return null;
    }
  }

  isFullyDone(jobId: string): boolean {
    const s = this.appliedMap.get(jobId);
    return s === 'REJECTED' || s === 'ARCHIVED';
  }

  ngOnInit(): void {
    // Set up debounced commute km save
    this.commuteKmSubject.pipe(debounceTime(800)).subscribe(km => {
      this.http.get<any>('/api/v1/users/me/preferences').subscribe({
        next: prefs => {
          this.http.put('/api/v1/users/me/preferences', { ...prefs, maxCommuteKm: km }).subscribe();
        }
      });
    });

    const saved = localStorage.getItem(JOB_FILTERS_KEY);
    if (saved) {
      try { this.filters = { ...DEFAULT_JOB_FILTERS, ...JSON.parse(saved) }; } catch {}
    } else {
      this.http.get<any>('/api/v1/users/me/preferences').subscribe({
        next: prefs => {
          this.maxCommuteKm = prefs.maxCommuteKm ?? null;
          if (!localStorage.getItem(JOB_FILTERS_KEY)) {
            this.filters = {
              ...DEFAULT_JOB_FILTERS,
              remoteTypes: prefs.preferredRemoteTypes ?? [],
              employmentTypes: prefs.preferredEmploymentTypes ?? [],
              seniorities: prefs.preferredSeniority ?? [],
              industries: prefs.preferredIndustries ?? [],
              salaryMin: prefs.salaryMin ?? null,
              salaryMax: prefs.salaryMax ?? null
            };
            if (this.activeFilterCount > 0) this.showFilters = true;
          }
        }
      });
    }
    if (this.activeFilterCount > 0) this.showFilters = true;

    // Load applications so we can show status badges
    this.appsApi.getAll().subscribe({
      next: apps => {
        this.appliedMap.clear();
        for (const app of apps) {
          // Keep the "most advanced" status if there are multiple apps for the same job
          const existing = this.appliedMap.get(app.jobId);
          if (!existing || STATUS_ORDER.indexOf(app.status) > STATUS_ORDER.indexOf(existing)) {
            this.appliedMap.set(app.jobId, app.status);
          }
        }
      }
    });

    this.searchCtrl.valueChanges.pipe(
      startWith(''),
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading = true;
        this.page = 0;
        const cats = this.filters.industries;
        return q
          ? this.api.search(q, 0, this.pageSize, cats)
          : cats.length
            ? this.api.search('*', 0, this.pageSize, cats)
            : this.api.getJobs(0, this.pageSize);
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

  toggleFilter(key: 'remoteTypes' | 'employmentTypes' | 'seniorities' | 'industries', value: string): void {
    const arr = this.filters[key];
    const idx = arr.indexOf(value);
    if (idx >= 0) arr.splice(idx, 1);
    else arr.push(value);
    this.saveFilters();
    // Re-trigger search when industry filter changes
    if (key === 'industries') {
      const q = this.searchCtrl.value ?? '';
      const cats = this.filters.industries;
      const obs: Observable<any> = (q || cats.length)
        ? this.api.search(q || '*', this.page, this.pageSize, cats)
        : this.api.getJobs(this.page, this.pageSize);
      runAction({
        action$: obs,
        setLoading: value => this.loading = value,
        next: (res: any) => {
          this.jobs = res.jobs ?? res.content ?? [];
          this.total = res.total ?? res.totalElements ?? 0;
        }
      });
    }
  }

  onFilterChange(): void {
    this.saveFilters();
  }

  onCommuteKmChange(value: number | null): void {
    this.commuteKmSubject.next(value);
  }

  clearFilters(): void {
    this.filters = { ...DEFAULT_JOB_FILTERS };
    localStorage.removeItem(JOB_FILTERS_KEY);
  }

  private saveFilters(): void {
    localStorage.setItem(JOB_FILTERS_KEY, JSON.stringify(this.filters));
  }

  loadPage(p: number): void {
    this.page = p;
    const q = this.searchCtrl.value ?? '';
    const cats = this.filters.industries;
    const obs: Observable<any> = (q || cats.length)
      ? this.api.search(q || '*', p, this.pageSize, cats)
      : this.api.getJobs(p, this.pageSize);
    runAction({
      action$: obs,
      setLoading: value => this.loading = value,
      next: (res: any) => {
        this.jobs = res.jobs ?? res.content ?? [];
        this.total = res.total ?? res.totalElements ?? 0;
      }
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

  duplicateCount(groupId: string): number {
    return this.filteredJobs.filter(j => j.duplicateGroupId === groupId).length;
  }
}

const STATUS_ORDER: ApplicationStatus[] = [
  'SAVED', 'PREPARING', 'APPLIED', 'RECRUITER_CONTACT',
  'INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND', 'OFFER',
  'REJECTED', 'ARCHIVED',
];
