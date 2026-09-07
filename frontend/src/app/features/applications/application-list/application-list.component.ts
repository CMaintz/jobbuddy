import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { Application } from '../../../core/models/application.model';

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Applications</h1>
        <a routerLink="/applications/pipeline" class="btn-secondary text-sm">Kanban View</a>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (apps.length === 0) {
        <div class="card text-center py-12">
          <p class="text-gray-500">No applications yet.</p>
          <a routerLink="/jobs/search" class="btn-primary mt-4 inline-block">Browse Jobs</a>
        </div>
      } @else {
        <div class="flex items-center gap-2">
          <input type="checkbox" id="showArchived" [(ngModel)]="showArchived" class="rounded border-gray-300" />
          <label for="showArchived" class="text-sm text-gray-600 cursor-pointer">Show archived &amp; rejected</label>
        </div>
        <div class="card overflow-hidden p-0">
          <table class="w-full text-sm">
            <thead class="bg-gray-50 border-b border-gray-200">
              <tr>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Application</th>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Status</th>
                <th class="text-left px-4 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wide">Applied</th>
                <th class="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              @for (app of visibleApps; track app.id) {
                <tr class="hover:bg-gray-50">
                  <td class="px-4 py-3">
                    <span class="font-medium text-gray-900">{{ app.jobTitle ?? app.jobCompanyName ?? ('Application #' + app.id.slice(0, 8)) }}</span>
                    @if (app.notes) {
                      <span class="ml-2 text-gray-400 text-xs">{{ app.notes }}</span>
                    }
                  </td>
                  <td class="px-4 py-3">
                    <span [class]="statusClass(app.status)"
                          class="text-xs font-medium px-2 py-1 rounded-full">
                      {{ app.status }}
                    </span>
                  </td>
                  <td class="px-4 py-3 text-gray-500">
                    {{ app.appliedAt ? (app.appliedAt | date:'mediumDate') : '—' }}
                  </td>
                  <td class="px-4 py-3 text-right">
                    <div class="flex items-center justify-end gap-2">
                      @if (app.status !== 'REJECTED' && app.status !== 'ARCHIVED') {
                        <button (click)="updateStatus(app, 'REJECTED')"
                                class="text-xs text-red-600 hover:text-red-800 hover:underline">
                          Reject
                        </button>
                        <button (click)="updateStatus(app, 'ARCHIVED')"
                                class="text-xs text-gray-500 hover:text-gray-700 hover:underline">
                          Archive
                        </button>
                      }
                      <a [routerLink]="['/applications', app.id]"
                         class="text-blue-600 hover:underline text-xs">View →</a>
                    </div>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `
})
export class ApplicationListComponent implements OnInit {
  private api = inject(ApplicationsApiService);

  apps: Application[] = [];
  loading = true;
  showArchived = false;

  get visibleApps(): Application[] {
    if (this.showArchived) return this.apps;
    return this.apps.filter(a => a.status !== 'REJECTED' && a.status !== 'ARCHIVED');
  }

  ngOnInit(): void {
    this.loadApps();
  }

  loadApps(): void {
    this.loading = true;
    this.api.getAll().subscribe({
      next: a => { this.apps = a; this.loading = false; },
      error: () => this.loading = false
    });
  }

  updateStatus(app: Application, status: string): void {
    this.api.updateStatus(app.id, status as any).subscribe({
      next: () => this.loadApps()
    });
  }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      SAVED: 'bg-gray-100 text-gray-600',
      PREPARING: 'bg-yellow-100 text-yellow-800',
      APPLIED: 'bg-blue-100 text-blue-800',
      RECRUITER_CONTACT: 'bg-indigo-100 text-indigo-800',
      INTERVIEW: 'bg-purple-100 text-purple-800',
      TECHNICAL_TEST: 'bg-orange-100 text-orange-800',
      FINAL_ROUND: 'bg-pink-100 text-pink-800',
      OFFER: 'bg-green-100 text-green-800',
      REJECTED: 'bg-red-100 text-red-700',
      ARCHIVED: 'bg-gray-100 text-gray-400'
    };
    return map[status] ?? 'bg-gray-100 text-gray-600';
  }
}
