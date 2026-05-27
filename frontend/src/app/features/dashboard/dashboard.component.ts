import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardApiService, DashboardData } from '../../core/api/dashboard.api';
import { RemindersApiService, FollowUpReminder } from '../../core/api/reminders.api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <h1 class="text-3xl font-bold text-gray-900">Dashboard</h1>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading dashboard...</div>
      } @else if (data) {
        <!-- Stats Row -->
        <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
          <a routerLink="/applications" class="card text-center hover:shadow-md transition-shadow">
            <div class="text-3xl font-bold text-blue-600">{{ data.appliedThisWeek ?? 0 }}</div>
            <div class="text-sm text-gray-500 mt-1">Applied This Week</div>
          </a>
          <a routerLink="/applications" class="card text-center hover:shadow-md transition-shadow">
            <div class="text-3xl font-bold text-yellow-600">{{ data.activeApplications ?? 0 }}</div>
            <div class="text-sm text-gray-500 mt-1">Active Applications</div>
          </a>
          <div class="card text-center">
            <div class="text-3xl font-bold text-purple-600">{{ data.upcomingInterviews.length }}</div>
            <div class="text-sm text-gray-500 mt-1">Interviews</div>
          </div>
          <div class="card text-center">
            <div class="text-3xl font-bold text-green-600">{{ data.recommendedJobs.length }}</div>
            <div class="text-sm text-gray-500 mt-1">Recommendations</div>
          </div>
        </div>

        <!-- Due Reminders -->
        @if (dueReminders.length) {
          <div class="card border-l-4"
               [class.border-red-400]="overdueReminders > 0"
               [class.border-yellow-400]="overdueReminders === 0">
            <div class="flex items-center justify-between mb-3">
              <h2 class="text-base font-semibold text-gray-900">Follow-up Reminders</h2>
              @if (overdueReminders > 0) {
                <span class="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-medium">
                  {{ overdueReminders }} overdue
                </span>
              }
            </div>
            <div class="space-y-2">
              @for (r of dueReminders; track r.id) {
                <div class="flex items-center justify-between p-2.5 rounded-lg"
                     [class.bg-red-50]="isOverdue(r)"
                     [class.bg-yellow-50]="!isOverdue(r)">
                  <div>
                    <p class="text-sm font-medium text-gray-900">{{ r.note || 'Follow up' }}</p>
                    <p class="text-xs" [class.text-red-600]="isOverdue(r)" [class.text-yellow-700]="!isOverdue(r)">
                      {{ reminderDueLabel(r) }}
                    </p>
                  </div>
                  <button (click)="completeReminder(r)"
                          class="text-xs text-green-600 hover:text-green-700 font-medium shrink-0 ml-4">
                    Mark done
                  </button>
                </div>
              }
            </div>
          </div>
        }

        <!-- Quick Actions -->
        <div class="card">
          <h2 class="text-lg font-semibold text-gray-900 mb-4">Quick Actions</h2>
          <div class="flex flex-wrap gap-3">
            <a routerLink="/jobs/search" class="btn-primary">Browse Jobs</a>
            <a routerLink="/jobs/add" class="btn-secondary">Add Job Manually</a>
            <a routerLink="/ai/generate" class="btn-secondary">Generate Application</a>
            <a routerLink="/ai/cv" class="btn-secondary">Upload CV</a>
            <a routerLink="/prompts" class="btn-secondary">Manage Prompts</a>
          </div>
        </div>

        <!-- Recommended Jobs -->
        @if (data.recommendedJobs.length) {
          <div class="card">
            <div class="flex justify-between items-center mb-4">
              <h2 class="text-lg font-semibold text-gray-900">Recommended Jobs</h2>
              <a routerLink="/jobs" class="text-sm text-blue-600 hover:underline">View all →</a>
            </div>
            <div class="space-y-3">
              @for (match of data.recommendedJobs.slice(0, 5); track match.jobId) {
                <div class="p-3 bg-gray-50 rounded-lg">
                  <div class="flex items-center justify-between">
                    <div>
                      <div class="font-medium text-gray-900">{{ match.job?.title }}</div>
                      <div class="text-sm text-gray-500">{{ match.job?.companyName }} • {{ match.job?.location }}</div>
                    </div>
                    <span class="text-xs font-medium px-2 py-1 rounded-full shrink-0 ml-2"
                          [class]="matchLabelClass(match.matchLabel)">
                      {{ match.totalScore }}%
                    </span>
                  </div>
                  @if (match.matchReasons?.length) {
                    <div class="mt-1.5 flex flex-wrap gap-x-4 gap-y-0.5">
                      @for (reason of match.matchReasons; track reason) {
                        <span class="text-xs text-green-700">✓ {{ reason }}</span>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          </div>
        }

        <!-- Active Applications -->
        @if (data.pendingApplications.length) {
          <div class="card">
            <div class="flex justify-between items-center mb-4">
              <h2 class="text-lg font-semibold text-gray-900">Active Applications</h2>
              <a routerLink="/applications" class="text-sm text-blue-600 hover:underline">View all →</a>
            </div>
            <div class="space-y-2">
              @for (app of data.pendingApplications.slice(0, 5); track app.id) {
                <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                  <span class="text-sm text-gray-700">Application #{{ app.id?.slice(0,8) }}</span>
                  <span class="text-xs font-medium bg-blue-100 text-blue-800 px-2 py-1 rounded-full">
                    {{ app.status }}
                  </span>
                </div>
              }
            </div>
          </div>
        }
      }
    </div>
  `
})
export class DashboardComponent implements OnInit {
  private api = inject(DashboardApiService);
  private remindersApi = inject(RemindersApiService);

  data: DashboardData | null = null;
  loading = true;
  dueReminders: FollowUpReminder[] = [];

  get overdueReminders(): number {
    const now = new Date();
    return this.dueReminders.filter(r => new Date(r.dueAt) < now).length;
  }

  ngOnInit(): void {
    this.api.getDashboard().subscribe({
      next: (d) => { this.data = d; this.loading = false; },
      error: () => this.loading = false
    });
    this.remindersApi.getDueReminders().subscribe({
      next: rs => this.dueReminders = rs,
      error: () => {}
    });
  }

  completeReminder(r: FollowUpReminder): void {
    this.remindersApi.complete(r.id).subscribe(() => {
      this.dueReminders = this.dueReminders.filter(item => item.id !== r.id);
    });
  }

  isOverdue(r: FollowUpReminder): boolean {
    return new Date(r.dueAt) < new Date();
  }

  reminderDueLabel(r: FollowUpReminder): string {
    const due = new Date(r.dueAt);
    const now = new Date();
    const diffMs = due.getTime() - now.getTime();
    const diffDays = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    if (diffDays < 0) return `Overdue by ${Math.abs(diffDays)} day${Math.abs(diffDays) !== 1 ? 's' : ''}`;
    if (diffDays === 0) return 'Due today';
    if (diffDays === 1) return 'Due tomorrow';
    return `Due in ${diffDays} days`;
  }

  matchLabelClass(label: string): string {
    const map: Record<string, string> = {
      EXCELLENT: 'bg-green-100 text-green-800',
      STRONG: 'bg-blue-100 text-blue-800',
      MODERATE: 'bg-yellow-100 text-yellow-800',
      WEAK: 'bg-gray-100 text-gray-600'
    };
    return map[label] ?? 'bg-gray-100 text-gray-600';
  }
}
