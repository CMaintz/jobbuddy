import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { Job, MatchResult } from '../../core/models/job.model';

interface FeedRow {
  id: string;
  title: string;
  company: string;
  location: string;
  salary?: string;
  matchScore?: number;
  matchReasons: string[];
  keywords: string[];
  source: string;
  posted: string;
  remote: boolean;
}

@Component({
  selector: 'app-job-feed',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, CompanyMarkComponent],
  templateUrl: './job-feed.component.html'
})
export class JobFeedComponent implements OnInit, OnDestroy {
  private jobsApi = inject(JobsApiService);

  loading = signal(true);
  toast = signal('');
  /** true while showing Typesense search results instead of recommendations */
  searchMode = signal(false);
  feedRows = signal<FeedRow[]>([]);
  selectedJob = signal<FeedRow | null>(null);
  mobilePanel = signal<'list' | 'detail'>('list');
  savedIds = signal<Set<string>>(new Set());

  query = '';
  minMatch = +(localStorage.getItem('jb-min-match') ?? 60);
  locationFilter = 'all';
  activeSources = new Set<string>();
  sourceFilters: string[] = [];

  private search$ = new Subject<string>();
  private searchSub?: Subscription;

  locationFilters = [
    { label: 'All', value: 'all' },
    { label: 'Remote only', value: 'remote' },
    { label: 'On-site', value: 'onsite' },
  ];

  ngOnInit(): void {
    this.loadRecommendations();

    this.searchSub = this.search$.pipe(
      debounceTime(350),
      distinctUntilChanged(),
    ).subscribe(q => this.runSearch(q));

    this.jobsApi.getSaved().subscribe({
      next: saved => this.savedIds.set(new Set(saved.map(j => j.id))),
      error: () => {}
    });
  }

  ngOnDestroy(): void {
    this.searchSub?.unsubscribe();
  }

  onQueryChange(q: string): void {
    this.search$.next(q.trim());
  }

  private loadRecommendations(): void {
    this.loading.set(true);
    this.jobsApi.getRecommendations(30).subscribe({
      next: results => {
        this.feedRows.set(results.map(r => this.matchToRow(r)));
        this.refreshSourceFilters();
        this.searchMode.set(false);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Could not load recommendations');
      }
    });
  }

  private runSearch(q: string): void {
    if (!q) {
      this.loadRecommendations();
      return;
    }
    this.loading.set(true);
    this.jobsApi.search(q, 0, 30).subscribe({
      next: result => {
        this.feedRows.set((result.jobs ?? []).map(j => this.jobToRow(j)));
        this.refreshSourceFilters();
        this.searchMode.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Search failed');
      }
    });
  }

  private matchToRow(r: MatchResult): FeedRow {
    const row = this.jobToRow(r.job);
    row.matchScore = r.totalScore;
    row.matchReasons = r.matchReasons ?? [];
    return row;
  }

  private jobToRow(job: Job): FeedRow {
    return {
      id: job.id,
      title: job.title,
      company: job.companyName ?? '—',
      location: job.location ?? (job.remoteType === 'REMOTE' ? 'Remote' : '—'),
      salary: job.salaryMin || job.salaryMax
        ? `${job.currency ?? ''} ${job.salaryMin ?? ''}–${job.salaryMax ?? ''}`.trim()
        : undefined,
      matchReasons: [],
      keywords: [...(job.technologies ?? []), ...(job.skills ?? [])].slice(0, 10),
      source: job.source ?? 'unknown',
      posted: this.ageLabel(job.postedAt),
      remote: job.remoteType === 'REMOTE',
    };
  }

  private refreshSourceFilters(): void {
    this.sourceFilters = [...new Set(this.feedRows().map(r => r.source))].sort();
    this.activeSources = new Set(this.sourceFilters);
  }

  private ageLabel(iso?: string): string {
    if (!iso) return '—';
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
    if (days <= 0) return 'today';
    if (days < 7) return `${days}d ago`;
    if (days < 30) return `${Math.floor(days / 7)}w ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }

  filteredJobs(): FeedRow[] {
    return this.feedRows().filter(row => {
      if (row.matchScore !== undefined && row.matchScore < this.minMatch) return false;
      if (this.locationFilter === 'remote' && !row.remote) return false;
      if (this.locationFilter === 'onsite' && row.remote) return false;
      if (this.activeSources.size > 0 && !this.activeSources.has(row.source)) return false;
      return true;
    });
  }

  toggleSource(src: string): void {
    if (this.activeSources.has(src)) this.activeSources.delete(src);
    else this.activeSources.add(src);
    this.activeSources = new Set(this.activeSources);
  }

  saveJob(row: FeedRow): void {
    if (this.savedIds().has(row.id)) return;
    this.jobsApi.save(row.id).subscribe({
      next: () => {
        this.savedIds.update(ids => new Set(ids).add(row.id));
        this.toast.set('Saved to your roles');
      },
      error: () => this.toast.set('Could not save the role')
    });
  }
}
