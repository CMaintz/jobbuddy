import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';
import { CompaniesApiService, Company } from '../../../core/api/companies.api';

@Component({
  selector: 'app-companies-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './companies-list.component.html'
})
export class CompaniesListComponent implements OnInit {
  private api = inject(CompaniesApiService);
  private search$ = new Subject<string>();

  companies: Company[] = [];
  query = '';
  loading = true;
  page = 0;
  pageSize = 21;
  total = 0;
  hasMore = false;

  ngOnInit(): void {
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        this.loading = true;
        this.page = 0;
        this.companies = [];
        return this.api.search(q, 0, this.pageSize);
      })
    ).subscribe({
      next: results => {
        this.companies = results;
        this.hasMore = results.length === this.pageSize;
        this.loading = false;
      },
      error: () => this.loading = false
    });

    this.api.search('', 0, this.pageSize).subscribe({
      next: results => {
        this.companies = results;
        this.hasMore = results.length === this.pageSize;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  onSearch(q: string): void {
    this.search$.next(q);
  }

  loadMore(): void {
    this.page++;
    this.api.search(this.query, this.page, this.pageSize).subscribe(results => {
      this.companies = [...this.companies, ...results];
      this.hasMore = results.length === this.pageSize;
    });
  }
}
