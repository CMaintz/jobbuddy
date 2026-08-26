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
import { CompaniesApiService, Company, OutreachTarget } from '../../core/api/companies.api';

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

  /** 'all' browses every known company; 'targets' ranks them for unsolicited applications. */
  view = signal<'all' | 'targets'>('all');
  targets = signal<OutreachTarget[]>([]);
  targetsLoading = signal(false);
  includeHiringNow = false;

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

  setView(view: 'all' | 'targets'): void {
    this.view.set(view);
    if (view === 'targets' && this.targets().length === 0) this.loadTargets();
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
  writeUnsolicited(target: OutreachTarget): void {
    this.router.navigate(['/apply'], { queryParams: { unsolicitedCompany: target.companyName } });
  }

  onQueryChange(q: string): void {
    this.search$.next(q.trim());
  }
}
