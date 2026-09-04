import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { Job, MatchResult } from '../../core/models/job.model';
import { MatchBadgeComponent, MatchLabel, matchColor } from '../../shared/components/match-badge/match-badge.component';

interface FeedRow {
  id: string;
  title: string;
  company: string;
  location: string;
  salary?: string;
  matchScore?: number;
  /** The backend's own verdict on the score — never re-derived here. */
  matchLabel?: MatchLabel;
  matchReasons: string[];
  keywords: string[];
  source: string;
  posted: string;
  /** ISO posting date, for the "newest" sort. */
  postedIso?: string;
  /** e.g. "Deadline 12 Aug" — undefined when the posting has none. */
  deadline?: string;
  /** ISO deadline date, for the deadline sort/filter. */
  deadlineIso?: string;
  deadlinePassed?: boolean;
  remote: boolean;
  seniority?: string;
  category?: string;
  /** The posting's opening, as the list response carries it. Never overwritten. */
  description?: string;
  /** True when the list response carried only the opening. */
  descriptionTruncated?: boolean;
  /** The whole posting, once fetched. Kept beside the preview so collapsing works. */
  fullDescription?: string;
}


@Component({
  selector: 'app-job-feed',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbTopbarComponent, JbButtonComponent, JbPillComponent, JbToastComponent, CompanyMarkComponent, MatchBadgeComponent],
  templateUrl: './job-feed.component.html'
})
export class JobFeedComponent implements OnInit, OnDestroy {
  /** Exposed for the detail pane, which colours a bare number rather than a ring. */
  matchColor = matchColor;

  private jobsApi = inject(JobsApiService);
  private translate = inject(TranslateService);

  loading = signal(true);
  toast = signal('');
  /** true while showing Typesense search results instead of recommendations */
  searchMode = signal(false);
  /** The filter set is collapsed by default so the list gets the width. */
  filtersOpen = signal(false);
  /** "View more" is per-selection: picking another role starts collapsed again. */
  descriptionExpanded = signal(false);
  /** The rest of the posting is being fetched. */
  descriptionLoading = signal(false);
  feedRows = signal<FeedRow[]>([]);
  selectedJob = signal<FeedRow | null>(null);
  /** Which way each job has been steered, so the control can show the current state. */
  steered = signal<Map<string, 'MORE_LIKE_THIS' | 'FEWER_LIKE_THIS'>>(new Map());
  mobilePanel = signal<'list' | 'detail'>('list');
  savedIds = signal<Set<string>>(new Set());

  query = '';
  minMatch = +(localStorage.getItem('jb-min-match') ?? 60);
  locationFilter = 'all';
  hideExpired = false;
  sortBy: 'match' | 'newest' | 'deadline' = 'match';
  /** true = embed the query and rank by meaning; false = Typesense keyword search. */
  semanticMode = false;
  activeSources = new Set<string>();
  sourceFilters: string[] = [];
  activeSeniorities = new Set<string>();
  seniorityFilters: string[] = [];
  activeCategories = new Set<string>();
  categoryFilters: string[] = [];

  private search$ = new Subject<string>();
  private searchSub?: Subscription;

  locationFilters = [
    { label: 'jobFeed.location.all', value: 'all' },
    { label: 'jobFeed.location.remote', value: 'remote' },
    { label: 'jobFeed.location.onsite', value: 'onsite' },
  ];

  sortOptions = [
    { label: 'jobFeed.sort.match', value: 'match' as const },
    { label: 'jobFeed.sort.newest', value: 'newest' as const },
    { label: 'jobFeed.sort.deadline', value: 'deadline' as const },
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
        this.toast.set(this.translate.instant('jobFeed.toast.loadFailed'));
      }
    });
  }

  private runSearch(q: string): void {
    if (!q) {
      this.loadRecommendations();
      return;
    }
    this.loading.set(true);
    if (this.semanticMode) {
      this.jobsApi.searchSemantic(q, 30).subscribe({
        next: jobs => {
          this.feedRows.set(jobs.map(j => this.jobToRow(j)));
          this.refreshSourceFilters();
          this.searchMode.set(true);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.toast.set(this.translate.instant('jobFeed.toast.semanticFailed'));
        }
      });
      return;
    }
    this.jobsApi.search(q, 0, 30).subscribe({
      next: result => {
        this.feedRows.set((result.jobs ?? []).map(j => this.jobToRow(j)));
        this.refreshSourceFilters();
        this.searchMode.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set(this.translate.instant('jobFeed.toast.searchFailed'));
      }
    });
  }

  toggleSemanticMode(): void {
    this.semanticMode = !this.semanticMode;
    if (this.query.trim()) this.runSearch(this.query.trim());
  }

  private matchToRow(r: MatchResult): FeedRow {
    const row = this.jobToRow(r.job);
    row.matchScore = r.totalScore;
    row.matchLabel = r.matchLabel;
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
      postedIso: job.postedAt,
      deadline: job.applicationDeadline
        ? new Date(job.applicationDeadline).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' })
        : undefined,
      deadlineIso: job.applicationDeadline,
      deadlinePassed: job.applicationDeadline ? new Date(job.applicationDeadline) < new Date() : undefined,
      remote: job.remoteType === 'REMOTE',
      seniority: job.seniority ?? undefined,
      category: job.jobCategory ?? undefined,
      description: job.descriptionClean ?? undefined,
      descriptionTruncated: job.descriptionTruncated ?? false,
    };
  }

  private refreshSourceFilters(): void {
    this.sourceFilters = [...new Set(this.feedRows().map(r => r.source))].sort();
    this.activeSources = new Set(this.sourceFilters);
    this.seniorityFilters = [...new Set(this.feedRows().map(r => r.seniority).filter((s): s is string => !!s))].sort();
    this.activeSeniorities = new Set(this.seniorityFilters);
    this.categoryFilters = [...new Set(this.feedRows().map(r => r.category).filter((c): c is string => !!c))].sort();
    this.activeCategories = new Set(this.categoryFilters);
  }

  private ageLabel(iso?: string): string {
    if (!iso) return '—';
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
    if (days <= 0) return this.translate.instant('time.today');
    if (days < 7) return this.translate.instant('time.daysAgo', { n: days });
    if (days < 30) return this.translate.instant('time.weeksAgo', { n: Math.floor(days / 7) });
    return this.translate.instant('time.monthsAgo', { n: Math.floor(days / 30) });
  }

  filteredJobs(): FeedRow[] {
    const rows = this.feedRows().filter(row => {
      if (row.matchScore !== undefined && row.matchScore < this.minMatch) return false;
      if (this.locationFilter === 'remote' && !row.remote) return false;
      if (this.locationFilter === 'onsite' && row.remote) return false;
      if (this.hideExpired && row.deadlinePassed) return false;
      if (this.activeSources.size > 0 && !this.activeSources.has(row.source)) return false;
      if (row.seniority && this.seniorityFilters.length > 1 && !this.activeSeniorities.has(row.seniority)) return false;
      if (row.category && this.categoryFilters.length > 1 && !this.activeCategories.has(row.category)) return false;
      return true;
    });
    return this.sortRows(rows);
  }

  /** 'match' keeps server order (already ranked); jobs without the sort key go last. */
  private sortRows(rows: FeedRow[]): FeedRow[] {
    if (this.sortBy === 'newest') {
      return [...rows].sort((a, b) =>
        (b.postedIso ? Date.parse(b.postedIso) : 0) - (a.postedIso ? Date.parse(a.postedIso) : 0));
    }
    if (this.sortBy === 'deadline') {
      return [...rows].sort((a, b) =>
        (a.deadlineIso ? Date.parse(a.deadlineIso) : Infinity)
        - (b.deadlineIso ? Date.parse(b.deadlineIso) : Infinity));
    }
    return rows;
  }

  toggleSource(src: string): void {
    if (this.activeSources.has(src)) this.activeSources.delete(src);
    else this.activeSources.add(src);
    this.activeSources = new Set(this.activeSources);
  }

  toggleSeniority(level: string): void {
    if (this.activeSeniorities.has(level)) this.activeSeniorities.delete(level);
    else this.activeSeniorities.add(level);
    this.activeSeniorities = new Set(this.activeSeniorities);
  }

  toggleCategory(cat: string): void {
    if (this.activeCategories.has(cat)) this.activeCategories.delete(cat);
    else this.activeCategories.add(cat);
    this.activeCategories = new Set(this.activeCategories);
  }

  categoryLabel(cat: string): string {
    return cat.replace(/_/g, ' ').toLowerCase();
  }

  /** Selecting a role collapses any expanded description from the previous one. */
  selectJob(row: FeedRow): void {
    this.selectedJob.set(row);
    this.descriptionExpanded.set(false);
    this.mobilePanel.set('detail');
  }

  /**
   * Collapsed shows the opening the list response carried; expanded shows the whole
   * posting once fetched. The two are kept apart so collapsing has something to go
   * back to.
   */
  descriptionText(row: FeedRow): string {
    if (this.descriptionExpanded() && row.fullDescription) return row.fullDescription;
    const opening = row.description ?? '';
    return this.hasMoreDescription(row) ? opening.trimEnd() + '…' : opening;
  }

  /** There is more posting than is on screen — either unfetched, or fetched and collapsed. */
  hasMoreDescription(row: FeedRow): boolean {
    return !!row.descriptionTruncated || !!row.fullDescription;
  }

  /**
   * Fetches the rest of the posting the first time it is asked for, then keeps it on
   * the row so collapsing and re-expanding costs nothing.
   */
  toggleDescription(row: FeedRow): void {
    if (this.descriptionExpanded()) {
      this.descriptionExpanded.set(false);
      return;
    }
    if (row.fullDescription || !row.descriptionTruncated) {
      this.descriptionExpanded.set(true);
      return;
    }
    this.descriptionLoading.set(true);
    this.jobsApi.getById(row.id).subscribe({
      next: job => {
        // The reader may have moved on while this was in flight.
        if (this.selectedJob()?.id !== row.id) { this.descriptionLoading.set(false); return; }
        row.fullDescription = job.descriptionClean ?? row.description;
        this.descriptionLoading.set(false);
        this.descriptionExpanded.set(true);
      },
      error: () => {
        this.descriptionLoading.set(false);
        this.toast.set(this.translate.instant('jobFeed.descriptionLoadFailed'));
      }
    });
  }

  /** How many filters are narrowing the list right now — shown on the collapsed toggle. */
  activeFilterCount(): number {
    let n = 0;
    if (!this.searchMode() && this.minMatch > 50) n++;
    if (this.locationFilter !== 'all') n++;
    if (this.hideExpired) n++;
    if (this.seniorityFilters.length > 1 && this.activeSeniorities.size < this.seniorityFilters.length) n++;
    if (this.categoryFilters.length > 1 && this.activeCategories.size < this.categoryFilters.length) n++;
    if (this.sourceFilters.length > 1 && this.activeSources.size < this.sourceFilters.length) n++;
    return n;
  }

  /** The current sort, shown on the filter bar so it stays visible while collapsed. */
  sortLabel(): string {
    return this.sortOptions.find(o => o.value === this.sortBy)?.label ?? 'jobFeed.sort.match';
  }

  resetFilters(): void {
    this.minMatch = 50;
    this.locationFilter = 'all';
    this.hideExpired = false;
    this.activeSeniorities = new Set(this.seniorityFilters);
    this.activeCategories = new Set(this.categoryFilters);
    this.activeSources = new Set(this.sourceFilters);
  }

  saveJob(row: FeedRow): void {
    if (this.savedIds().has(row.id)) return;
    this.jobsApi.save(row.id).subscribe({
      next: () => {
        this.savedIds.update(ids => new Set(ids).add(row.id));
        this.toast.set(this.translate.instant('jobFeed.toast.saved'));
      },
      error: () => this.toast.set(this.translate.instant('jobFeed.toast.saveFailed'))
    });
  }

  /**
   * Steering: a statement about the kind of role, not this one.
   *
   * The job stays in the feed — you are shaping what comes next, not dismissing what is in front
   * of you. Dismissing is what "Not interested" is for.
   */
  steerFeed(row: FeedRow, type: 'MORE_LIKE_THIS' | 'FEWER_LIKE_THIS'): void {
    this.steered.update(m => new Map(m).set(row.id, type));
    this.jobsApi.submitFeedback(row.id, type).subscribe({
      next: () => this.toast.set(this.translate.instant(
        type === 'MORE_LIKE_THIS' ? 'jobFeed.toast.moreLikeThis' : 'jobFeed.toast.fewerLikeThis')),
      error: () => {
        this.steered.update(m => { const next = new Map(m); next.delete(row.id); return next; });
        this.toast.set(this.translate.instant('jobFeed.toast.steerFailed'));
      }
    });
  }

  /** Not interested: drop the role from this user's feed (server-side ignore). */
  hideJob(row: FeedRow): void {
    this.feedRows.update(rows => rows.filter(r => r.id !== row.id));
    if (this.selectedJob()?.id === row.id) {
      this.selectedJob.set(null);
      this.mobilePanel.set('list');
    }
    this.jobsApi.ignore(row.id, 'not interested').subscribe({
      next: () => this.toast.set(this.translate.instant('jobFeed.toast.hidden')),
      error: () => this.toast.set(this.translate.instant('jobFeed.toast.hideFailed'))
    });
  }
}
