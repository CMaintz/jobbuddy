import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { StatusChipComponent } from '../../shared/components/status-chip/status-chip.component';
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
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent, JbPillComponent, StatusChipComponent, CompanyMarkComponent, FitBarComponent, JbTopbarComponent],
  templateUrl: './pipeline.component.html'
})
export class PipelineComponent implements OnInit {
  private api = inject(ApplicationsApiService);
  private translate = inject(TranslateService);

  applications: Application[] = [];
  loading = true;
  view = signal<'table' | 'kanban'>('table');
  activeFilters = signal<Set<ApplicationStatus>>(new Set());
  search = signal('');

  stages: StageConfig[] = [
    { key: 'SAVED', label: 'pipeline.stage.saved', tone: 'neutral' },
    { key: 'APPLIED', label: 'pipeline.stage.applied', tone: 'info' },
    { key: 'RECRUITER_CONTACT', label: 'pipeline.stage.screen', tone: 'violet' },
    { key: 'INTERVIEW', label: 'pipeline.stage.interview', tone: 'accent' },
    { key: 'OFFER', label: 'pipeline.stage.offer', tone: 'success' },
    { key: 'REJECTED', label: 'pipeline.stage.rejected', tone: 'danger' },
  ];

  kanbanStages: StageConfig[] = [
    { key: 'SAVED', label: 'pipeline.stage.saved', tone: 'neutral' },
    { key: 'APPLIED', label: 'pipeline.stage.applied', tone: 'info' },
    { key: 'INTERVIEW', label: 'pipeline.stage.interview', tone: 'accent' },
    { key: 'OFFER', label: 'pipeline.stage.offer', tone: 'success' },
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


  ageLabel(dateStr: string): string {
    if (!dateStr) return '—';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days === 0) return this.translate.instant('time.today');
    if (days < 7) return this.translate.instant('time.compact.days', { n: days });
    if (days < 30) return this.translate.instant('time.compact.weeks', { n: Math.floor(days / 7) });
    return this.translate.instant('time.compact.months', { n: Math.floor(days / 30) });
  }
}
