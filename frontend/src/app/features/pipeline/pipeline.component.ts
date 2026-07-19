import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';

interface StageConfig {
  key: ApplicationStatus;
  label: string;
  tone: 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet';
}

@Component({
  selector: 'app-pipeline',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent, FitBarComponent, JbTopbarComponent],
  templateUrl: './pipeline.component.html'
})
export class PipelineComponent implements OnInit {
  private api = inject(ApplicationsApiService);

  applications: Application[] = [];
  loading = true;
  view = signal<'table' | 'kanban'>('table');
  activeFilters = signal<Set<ApplicationStatus>>(new Set());
  search = signal('');

  stages: StageConfig[] = [
    { key: 'SAVED', label: 'Saved', tone: 'neutral' },
    { key: 'APPLIED', label: 'Applied', tone: 'info' },
    { key: 'RECRUITER_CONTACT', label: 'Screen', tone: 'violet' },
    { key: 'INTERVIEW', label: 'Interview', tone: 'accent' },
    { key: 'OFFER', label: 'Offer', tone: 'success' },
    { key: 'REJECTED', label: 'Rejected', tone: 'danger' },
  ];

  kanbanStages: StageConfig[] = [
    { key: 'SAVED', label: 'Saved', tone: 'neutral' },
    { key: 'APPLIED', label: 'Applied', tone: 'info' },
    { key: 'INTERVIEW', label: 'Interview', tone: 'accent' },
    { key: 'OFFER', label: 'Offer', tone: 'success' },
  ];

  ngOnInit(): void {
    this.api.getAll().subscribe({
      next: (apps) => { this.applications = apps; this.loading = false; },
      error: () => this.loading = false
    });
  }

  toggleStageFilter(stage: ApplicationStatus): void {
    const current = new Set(this.activeFilters());
    if (current.has(stage)) current.delete(stage);
    else current.add(stage);
    this.activeFilters.set(current);
  }

  filteredApplications(): Application[] {
    const filters = this.activeFilters();
    let apps = this.searchedApplications();
    if (filters.size > 0) apps = apps.filter(a => filters.has(a.status));
    return apps;
  }

  countByStage(stage: ApplicationStatus): number {
    return this.searchedApplications().filter(a => a.status === stage).length;
  }

  appsForStage(stage: ApplicationStatus): Application[] {
    return this.searchedApplications().filter(a => a.status === stage);
  }

  private searchedApplications(): Application[] {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.applications;
    return this.applications.filter(a =>
      (a.jobCompanyName ?? '').toLowerCase().includes(q) ||
      (a.jobTitle ?? '').toLowerCase().includes(q) ||
      (a.notes ?? '').toLowerCase().includes(q));
  }

  stageTone(status: ApplicationStatus): 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet' {
    const map: Record<string, any> = {
      SAVED: 'neutral', PREPARING: 'neutral', APPLIED: 'info',
      RECRUITER_CONTACT: 'violet', INTERVIEW: 'accent', TECHNICAL_TEST: 'accent',
      FINAL_ROUND: 'accent', OFFER: 'success', REJECTED: 'danger', ARCHIVED: 'neutral'
    };
    return map[status] ?? 'neutral';
  }

  stageLabel(status: ApplicationStatus): string {
    const map: Record<string, string> = {
      SAVED: 'Saved', PREPARING: 'Preparing', APPLIED: 'Applied',
      RECRUITER_CONTACT: 'Screen', INTERVIEW: 'Interview', TECHNICAL_TEST: 'Technical',
      FINAL_ROUND: 'Final', OFFER: 'Offer', REJECTED: 'Rejected', ARCHIVED: 'Archived'
    };
    return map[status] ?? status;
  }

  ageLabel(dateStr: string): string {
    if (!dateStr) return '—';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days === 0) return 'today';
    if (days === 1) return '1d';
    if (days < 7) return `${days}d`;
    if (days < 30) return `${Math.floor(days / 7)}w`;
    return `${Math.floor(days / 30)}mo`;
  }
}
