import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { CompaniesApiService, Company } from '../../core/api/companies.api';

@Component({
  selector: 'app-companies',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbTopbarComponent, JbPillComponent, CompanyMarkComponent],
  templateUrl: './companies.component.html'
})
export class CompaniesComponent implements OnInit, OnDestroy {
  private companiesApi = inject(CompaniesApiService);

  loading = signal(true);
  companies = signal<Company[]>([]);
  selected = signal<Company | null>(null);
  query = '';

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

  onQueryChange(q: string): void {
    this.search$.next(q.trim());
  }
}
