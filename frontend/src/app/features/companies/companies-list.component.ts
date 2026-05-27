import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, Subject, switchMap } from 'rxjs';
import { CompaniesApiService, Company } from '../../core/api/companies.api';

@Component({
  selector: 'app-companies-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Companies</h1>
        <span class="text-sm text-gray-500">{{ total }} companies</span>
      </div>

      <input
        type="text"
        [(ngModel)]="query"
        (ngModelChange)="onSearch($event)"
        placeholder="Search companies..."
        class="w-full border rounded-lg px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading companies...</div>
      } @else if (companies.length === 0) {
        <div class="text-center py-12 text-gray-400">No companies found.</div>
      } @else {
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          @for (c of companies; track c.id) {
            <div class="card hover:shadow-md transition-shadow">
              <div class="flex items-start justify-between gap-2">
                <div class="flex-1 min-w-0">
                  <h2 class="font-semibold text-gray-900 truncate">{{ c.name }}</h2>
                  @if (c.industry) {
                    <p class="text-sm text-gray-500">{{ c.industry }}</p>
                  }
                </div>
                <div class="flex flex-col gap-1 shrink-0">
                  @if (c.isConsulting) {
                    <span class="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full">Consulting</span>
                  }
                  @if (c.isRecruitingAgency) {
                    <span class="text-xs bg-orange-100 text-orange-700 px-2 py-0.5 rounded-full">Agency</span>
                  }
                </div>
              </div>
              @if (c.description) {
                <p class="text-sm text-gray-600 mt-2 line-clamp-2">{{ c.description }}</p>
              }
              <div class="flex flex-wrap gap-3 mt-3 text-xs text-gray-500">
                @if (c.sizeRange) { <span>{{ c.sizeRange }}</span> }
                @if (c.country) { <span>{{ c.country }}</span> }
              </div>
              @if (c.website) {
                <a [href]="c.website" target="_blank" rel="noopener"
                   class="mt-3 inline-block text-xs text-blue-600 hover:underline truncate max-w-full">
                  {{ c.website }}
                </a>
              }
            </div>
          }
        </div>

        @if (hasMore) {
          <div class="text-center">
            <button (click)="loadMore()" class="btn-secondary text-sm">Load more</button>
          </div>
        }
      }
    </div>
  `
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
