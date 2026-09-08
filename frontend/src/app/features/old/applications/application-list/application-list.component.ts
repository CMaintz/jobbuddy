import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { Application } from '../../../../core/models/application.model';

@Component({
  selector: 'app-application-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './application-list.component.html'
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
