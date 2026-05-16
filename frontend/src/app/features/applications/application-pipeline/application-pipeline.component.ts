import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../../core/models/application.model';

const COLUMNS: { status: ApplicationStatus; label: string; color: string }[] = [
  { status: 'SAVED', label: 'Saved', color: 'bg-gray-100' },
  { status: 'PREPARING', label: 'Preparing', color: 'bg-yellow-100' },
  { status: 'APPLIED', label: 'Applied', color: 'bg-blue-100' },
  { status: 'RECRUITER_CONTACT', label: 'Recruiter', color: 'bg-indigo-100' },
  { status: 'INTERVIEW', label: 'Interview', color: 'bg-purple-100' },
  { status: 'OFFER', label: 'Offer', color: 'bg-green-100' },
  { status: 'REJECTED', label: 'Rejected', color: 'bg-red-100' }
];

@Component({
  selector: 'app-application-pipeline',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Pipeline</h1>
        <a routerLink="/applications" class="btn-secondary text-sm">List View</a>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else {
        <div class="flex gap-3 overflow-x-auto pb-4">
          @for (col of columns; track col.status) {
            <div class="flex-shrink-0 w-72">
              <div class="flex items-center justify-between mb-2 px-1">
                <h3 class="text-xs font-semibold text-gray-600 uppercase tracking-wide">{{ col.label }}</h3>
                <span class="text-xs text-gray-400 bg-gray-100 rounded-full px-1.5 py-0.5">
                  {{ byStatus(col.status).length }}
                </span>
              </div>
              <div class="space-y-2 min-h-20">
                @for (app of byStatus(col.status); track app.id) {
                  <a [routerLink]="['/applications', app.id]"
                     [class]="col.color"
                     class="block rounded-lg p-3 hover:opacity-80 transition-opacity cursor-pointer">
                    <div class="text-xs font-medium text-gray-800">#{{ app.id.slice(0,8) }}</div>
                    @if (app.notes) {
                      <div class="text-xs text-gray-500 mt-0.5 truncate">{{ app.notes }}</div>
                    }
                    @if (app.appliedAt) {
                      <div class="text-xs text-gray-400 mt-1">{{ app.appliedAt | date:'d MMM' }}</div>
                    }
                  </a>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class ApplicationPipelineComponent implements OnInit {
  private api = inject(ApplicationsApiService);

  apps: Application[] = [];
  loading = true;
  columns = COLUMNS;

  ngOnInit(): void {
    this.api.getAll().subscribe({
      next: a => { this.apps = a; this.loading = false; },
      error: () => this.loading = false
    });
  }

  byStatus(status: ApplicationStatus): Application[] {
    return this.apps.filter(a => a.status === status);
  }
}
