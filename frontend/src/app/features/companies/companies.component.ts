import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { Router, RouterLink } from '@angular/router';
import { Job } from '../../core/models/job.model';
import { CompaniesApiService, Company, OutreachContact, OutreachStatus, OutreachTarget }
  from '../../core/api/companies.api';

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [
    CommonModule, FormsModule, TranslateModule, JbIconComponent, JbTopbarComponent, JbPillComponent,
    CompanyMarkComponent, RouterLink,
  ],
  templateUrl: './companies.component.html'
})
export class CompaniesComponent implements OnInit, OnDestroy {
  private companiesApi = inject(CompaniesApiService);
  private router = inject(Router);

  loading = signal(true);
  companies = signal<Company[]>([]);
  selected = signal<Company | null>(null);
  query = '';

  /** Postings we hold from the selected company; reloaded whenever the selection changes. */
  companyJobs = signal<Job[]>([]);
  companyJobsLoading = signal(false);

  /** The candidate's research notes for the selected company; grounds cover letters. */
  researchNotes = signal('');
  researchUpdatedAt = signal<string | null>(null);
  researchSaving = signal(false);
  researchLoading = signal(false);
  /** A failed load must NOT look like "no notes" — saving blank then would wipe real research. */
  researchLoadFailed = signal(false);
  /** Set once the user edits, so a slow load can't overwrite what they've started typing. */
  researchDirty = signal(false);

  /**
   * 'all' browses every known company, 'targets' ranks them for unsolicited applications, and
   * 'tracked' is the outreach actually under way.
   */
  view = signal<'all' | 'targets' | 'tracked'>('all');
  targets = signal<OutreachTarget[]>([]);
  targetsLoading = signal(false);
  includeHiringNow = false;
  tracked = signal<OutreachContact[]>([]);
  trackedLoading = signal(false);

  /** Selecting a company also pulls the postings we hold from them and its research notes. */
  select(company: Company): void {
    this.selected.set(company);
    this.companyJobs.set([]);
    this.companyJobsLoading.set(true);
    this.researchNotes.set('');
    this.researchUpdatedAt.set(null);
    this.researchDirty.set(false);
    this.researchLoadFailed.set(false);
    this.researchLoading.set(true);
    this.companiesApi.jobs(company.id).subscribe({
      next: jobs => {
        // A slower request for a company the user has since clicked away from must not
        // overwrite the one they are looking at now.
        if (this.selected()?.id !== company.id) return;
        this.companyJobs.set(jobs);
        this.companyJobsLoading.set(false);
      },
      error: () => {
        if (this.selected()?.id === company.id) this.companyJobsLoading.set(false);
      }
    });
    this.companiesApi.getResearch(company.id).subscribe({
      next: res => {
        if (this.selected()?.id !== company.id) return;
        this.researchLoading.set(false);
        // The user may have started typing before this arrived — don't clobber their edit.
        if (this.researchDirty()) return;
        this.researchNotes.set(res.notes ?? '');
        this.researchUpdatedAt.set(res.updatedAt);
      },
      error: () => {
        if (this.selected()?.id !== company.id) return;
        this.researchLoading.set(false);
        this.researchLoadFailed.set(true);
      },
    });
  }

  /** Track edits so a late load can't overwrite them, and clear a prior load failure. */
  onResearchInput(value: string): void {
    this.researchNotes.set(value);
    this.researchDirty.set(true);
    this.researchLoadFailed.set(false);
  }

  /** Persist the research notes for the selected company; blank clears them. */
  saveResearch(): void {
    const company = this.selected();
    // Never save while a load is in flight or failed: a blank save would wipe real notes.
    if (!company || this.researchSaving() || this.researchLoading() || this.researchLoadFailed()) return;
    this.researchSaving.set(true);
    this.companiesApi.saveResearch(company.id, this.researchNotes()).subscribe({
      next: res => {
        if (this.selected()?.id === company.id) {
          this.researchUpdatedAt.set(res.updatedAt);
          this.researchDirty.set(false);
        }
        this.researchSaving.set(false);
      },
      error: () => this.researchSaving.set(false),
    });
  }

  readonly outreachStatuses: OutreachStatus[] = ['SAVED', 'CONTACTED', 'REPLIED', 'MEETING', 'CLOSED'];

  private search$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading.set(true);
        return this.companiesApi.search(q, 0, 50);
      }),
    ).subscribe({
      next: companies => {
        this.companies.set(companies);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
    this.search$.next('');
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  setView(view: 'all' | 'targets' | 'tracked'): void {
    this.view.set(view);
    if (view === 'targets' && this.targets().length === 0) this.loadTargets();
    if (view === 'tracked') this.loadTracked();
  }

  loadTracked(): void {
    this.trackedLoading.set(true);
    this.companiesApi.listOutreach().subscribe({
      next: rows => { this.tracked.set(rows); this.trackedLoading.set(false); },
      error: () => this.trackedLoading.set(false),
    });
  }

  /** Start tracking a suggestion; it leaves the ranked list, which no longer re-suggests it. */
  track(target: OutreachTarget): void {
    this.companiesApi.trackOutreach(target.companyId, target.companyName).subscribe({
      next: () => {
        this.targets.set(this.targets().filter(t => t.companyId !== target.companyId));
        this.tracked.set([]);
      },
    });
  }

  setStatus(contact: OutreachContact, status: OutreachStatus): void {
    this.companiesApi.updateOutreach(contact.id, { status }).subscribe({
      next: updated => this.tracked.set(this.tracked().map(c => c.id === updated.id ? updated : c)),
    });
  }

  untrack(contact: OutreachContact): void {
    this.companiesApi.untrackOutreach(contact.id).subscribe({
      next: () => this.tracked.set(this.tracked().filter(c => c.id !== contact.id)),
    });
  }

  /** A follow-up is due when its date has arrived and the thread is still open. */
  isFollowUpDue(contact: OutreachContact): boolean {
    if (!contact.followUpDue || contact.status === 'CLOSED') return false;
    return new Date(contact.followUpDue) <= new Date();
  }

  loadTargets(): void {
    this.targetsLoading.set(true);
    this.companiesApi.outreachTargets(20, this.includeHiringNow).subscribe({
      next: targets => {
        this.targets.set(targets);
        this.targetsLoading.set(false);
      },
      error: () => this.targetsLoading.set(false),
    });
  }

  /** Straight into the unsolicited flow with the company filled in — there is no posting to paste. */
  writeUnsolicited(target: OutreachTarget | OutreachContact): void {
    this.router.navigate(['/apply'], { queryParams: { unsolicitedCompany: target.companyName } });
  }

  onQueryChange(q: string): void {
    this.search$.next(q.trim());
  }
}
