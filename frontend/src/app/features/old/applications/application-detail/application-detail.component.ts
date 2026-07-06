import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../../../core/models/application.model';

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
  templateUrl: './application-detail.component.html'
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
