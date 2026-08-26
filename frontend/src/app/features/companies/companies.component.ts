import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { Router } from '@angular/router';
import { CompaniesApiService, Company, OutreachContact, OutreachStatus, OutreachTarget }
  from '../../core/api/companies.api';

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbTopbarComponent, JbPillComponent, CompanyMarkComponent],
  templateUrl: './companies.component.html'
})
export class CompaniesComponent implements OnInit, OnDestroy {
  private companiesApi = inject(CompaniesApiService);
  private router = inject(Router);

  loading = signal(true);
  companies = signal<Company[]>([]);
  selected = signal<Company | null>(null);
  query = '';

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
