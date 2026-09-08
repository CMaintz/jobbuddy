import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApplicationsApiService } from '../../../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../../../core/models/application.model';

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
  templateUrl: './application-pipeline.component.html'
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
