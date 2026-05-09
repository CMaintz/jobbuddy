import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JobsApiService, IgnoredJob } from '../../../core/api/jobs.api';

@Component({
  selector: 'app-ignored-jobs',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Hidden Jobs</h1>
        <span class="text-sm text-gray-500">{{ items.length }} hidden</span>
      </div>

      <p class="text-sm text-gray-500">Jobs you've hidden will not appear in browse or recommendations.
        You can un-hide them here at any time.</p>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (items.length === 0) {
        <div class="text-center py-12 text-gray-400">You haven't hidden any jobs yet.</div>
      } @else {
        <div class="space-y-2">
          @for (item of items; track item.id) {
            <div class="card flex items-center justify-between gap-3">
              <div class="flex-1 min-w-0">
                <a [routerLink]="['/jobs', item.jobId]"
                   class="text-blue-600 hover:underline font-medium text-sm">
                  View job
                </a>
                @if (item.reason) {
                  <span class="text-xs text-gray-400 ml-2">— {{ item.reason }}</span>
                }
                <span class="text-xs text-gray-400 ml-2">
                  Hidden {{ item.ignoredAt | date:'mediumDate' }}
                </span>
              </div>
              <button (click)="unignore(item)"
                      class="text-xs text-blue-600 hover:text-blue-800 border border-blue-200 px-3 py-1 rounded shrink-0">
                Un-hide
              </button>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class IgnoredJobsComponent implements OnInit {
  private api = inject(JobsApiService);

  items: IgnoredJob[] = [];
  loading = true;

  ngOnInit(): void {
    this.api.getIgnored().subscribe({
      next: items => { this.items = items; this.loading = false; },
      error: () => this.loading = false
    });
  }

  unignore(item: IgnoredJob): void {
    this.api.unignore(item.jobId).subscribe(() => {
      this.items = this.items.filter(i => i.id !== item.id);
    });
  }
}
