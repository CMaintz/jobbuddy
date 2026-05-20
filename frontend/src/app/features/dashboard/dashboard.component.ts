import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardApiService, DashboardData } from '../../core/api/dashboard.api';

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
  data: DashboardData | null = null;
  loading = true;

  ngOnInit(): void {
    this.api.getDashboard().subscribe({
      next: (d) => { this.data = d; this.loading = false; },
      error: () => this.loading = false
    });
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
