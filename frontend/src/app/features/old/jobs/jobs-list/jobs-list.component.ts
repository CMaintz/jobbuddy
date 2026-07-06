import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { Observable, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, startWith } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { JobsApiService } from '../../../../core/api/jobs.api';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { Job } from '../../../../core/models/job.model';
import { ApplicationStatus } from '../../../../core/models/application.model';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../../shared/utils/async-ui';
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
  templateUrl: './jobs-list.component.html'
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
