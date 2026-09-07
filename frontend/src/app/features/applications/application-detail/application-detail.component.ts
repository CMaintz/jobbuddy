import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ApplicationsApiService } from '../../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../../core/models/application.model';

const NEXT_STATUSES: Partial<Record<ApplicationStatus, ApplicationStatus[]>> = {
  SAVED: ['PREPARING', 'ARCHIVED'],
  PREPARING: ['APPLIED', 'SAVED', 'ARCHIVED'],
  APPLIED: ['RECRUITER_CONTACT', 'REJECTED', 'ARCHIVED'],
  RECRUITER_CONTACT: ['INTERVIEW', 'REJECTED', 'ARCHIVED'],
  INTERVIEW: ['TECHNICAL_TEST', 'FINAL_ROUND', 'REJECTED', 'ARCHIVED'],
  TECHNICAL_TEST: ['FINAL_ROUND', 'REJECTED', 'ARCHIVED'],
  FINAL_ROUND: ['OFFER', 'REJECTED', 'ARCHIVED'],
  OFFER: ['ARCHIVED']
};

@Component({
  selector: 'app-application-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <a routerLink="/applications" class="text-sm text-blue-600 hover:underline">← Back to applications</a>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (!app) {
        <div class="text-center py-12 text-gray-500">Application not found.</div>
      } @else {
        <div class="card space-y-4">
          <h1 class="text-2xl font-bold text-gray-900">Application #{{ app.id.slice(0,8) }}</h1>

          <div class="flex items-center gap-3">
            <span [class]="statusClass(app.status)"
                  class="text-sm font-medium px-3 py-1 rounded-full">
              {{ app.status }}
            </span>
            @if (app.appliedAt) {
              <span class="text-sm text-gray-500">Applied {{ app.appliedAt | date:'mediumDate' }}</span>
            }
          </div>

          @if (app.recruiterName || app.recruiterEmail) {
            <div class="bg-gray-50 rounded-lg p-3 text-sm">
              <p class="font-medium text-gray-700">Recruiter</p>
              @if (app.recruiterName) { <p>{{ app.recruiterName }}</p> }
              @if (app.recruiterEmail) {
                <a [href]="'mailto:' + app.recruiterEmail" class="text-blue-600 hover:underline">
                  {{ app.recruiterEmail }}
                </a>
              }
            </div>
          }

          @if (app.notes) {
            <div>
              <p class="text-sm font-medium text-gray-700 mb-1">Notes</p>
              <p class="text-sm text-gray-600">{{ app.notes }}</p>
            </div>
          }

          <!-- Status transition -->
          @if (nextStatuses.length > 0) {
            <div class="border-t border-gray-100 pt-4">
              <p class="text-sm font-semibold text-gray-700 mb-2">Update status</p>
              <div class="flex flex-wrap gap-2">
                @for (s of nextStatuses; track s) {
                  <button (click)="transition(s)"
                          [class]="statusClass(s)"
                          class="text-xs font-medium px-3 py-1.5 rounded-full border border-transparent hover:opacity-80 transition-opacity">
                    → {{ s }}
                  </button>
                }
              </div>
            </div>
          }
        </div>

        <div class="card">
          <h2 class="text-base font-semibold text-gray-900 mb-3">Quick Actions</h2>
          <div class="flex flex-wrap gap-2">
            <a [routerLink]="['/ai/generate']" [queryParams]="{jobId: app.jobId}"
               class="btn-primary text-sm">Generate Cover Letter</a>
            <a [routerLink]="['/jobs', app.jobId]" class="btn-secondary text-sm">View Job</a>
          </div>
        </div>
      }
    </div>
  `
})
export class ApplicationDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApplicationsApiService);

  app: Application | null = null;
  loading = true;

  get nextStatuses(): ApplicationStatus[] {
    if (!this.app) return [];
    return NEXT_STATUSES[this.app.status] ?? [];
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getById(id).subscribe({
      next: a => { this.app = a; this.loading = false; },
      error: () => this.loading = false
    });
  }

  transition(status: ApplicationStatus): void {
    if (!this.app) return;
    this.api.updateStatus(this.app.id, status).subscribe({
      next: a => this.app = a
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
