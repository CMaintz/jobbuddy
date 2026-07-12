import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JbDropdownComponent } from '../../shared/components/jb-dropdown/jb-dropdown.component';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { AiApiService } from '../../core/api/ai.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';

const STAGE_LABEL: Record<string, string> = {
  SAVED: 'Saved', PREPARING: 'Preparing', APPLIED: 'Applied',
  RECRUITER_CONTACT: 'Screen', INTERVIEW: 'Interview', TECHNICAL_TEST: 'Technical',
  FINAL_ROUND: 'Final', OFFER: 'Offer', REJECTED: 'Rejected', ARCHIVED: 'Archived'
};
const STAGE_TONE: Record<string, string> = {
  SAVED: 'neutral', PREPARING: 'neutral', APPLIED: 'info',
  RECRUITER_CONTACT: 'violet', INTERVIEW: 'accent', TECHNICAL_TEST: 'accent',
  FINAL_ROUND: 'accent', OFFER: 'success', REJECTED: 'danger', ARCHIVED: 'neutral'
};

@Component({
  selector: 'app-applications-list',
  standalone: true,
  imports: [CommonModule, RouterLink, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent, FitBarComponent, JbDropdownComponent],
  templateUrl: './applications-list.component.html'
})
export class ApplicationsListComponent implements OnInit {
  private api = inject(ApplicationsApiService);
  private aiApi = inject(AiApiService);

  applications: Application[] = [];
  loading = true;
  activeFilter = signal('all');
  exporting = signal(false);
  openIds = new Set<string>();

  filters: { key: string; label: string; count: number }[] = [];

  ngOnInit(): void {
    this.api.getAll().subscribe({
      next: (apps) => {
        this.applications = apps;
        this.loading = false;
        this.updateFilterCounts();
      },
      error: () => this.loading = false
    });
  }

  private updateFilterCounts(): void {
    const apps = this.applications;
    this.filters = [
      { key: 'all', label: 'All', count: apps.length },
      { key: 'active', label: 'Active', count: apps.filter(a => !['REJECTED', 'ARCHIVED', 'SAVED'].includes(a.status)).length },
      { key: 'drafts', label: 'Drafts', count: apps.filter(a => a.status === 'SAVED' || a.status === 'PREPARING').length },
      { key: 'closed', label: 'Closed', count: apps.filter(a => a.status === 'REJECTED' || a.status === 'ARCHIVED').length },
    ];
  }

  filteredApps(): Application[] {
    const f = this.activeFilter();
    if (f === 'active') return this.applications.filter(a => !['REJECTED', 'ARCHIVED', 'SAVED'].includes(a.status));
    if (f === 'drafts') return this.applications.filter(a => a.status === 'SAVED' || a.status === 'PREPARING');
    if (f === 'closed') return this.applications.filter(a => a.status === 'REJECTED' || a.status === 'ARCHIVED');
    return this.applications;
  }

  toggleOpen(id: string): void {
    if (this.openIds.has(id)) this.openIds.delete(id);
    else this.openIds.add(id);
    this.openIds = new Set(this.openIds);
  }

  stageLabel(status: ApplicationStatus): string { return STAGE_LABEL[status] ?? status; }
  stageTone(status: ApplicationStatus): any { return STAGE_TONE[status] ?? 'neutral'; }

  ageLabel(dateStr: string): string {
    if (!dateStr) return '—';
    const diff = Date.now() - new Date(dateStr).getTime();
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    if (days === 0) return 'today';
    if (days === 1) return '1d ago';
    if (days < 7) return `${days}d ago`;
    if (days < 30) return `${Math.floor(days / 7)}w ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }

  // ── Export all ────────────────────────────────────────────────

  exportCsv(): void {
    const header = ['Company', 'Role', 'Stage', 'Fit score', 'Created', 'Applied', 'Updated', 'Notes'];
    const rows = this.applications.map(a => [
      a.jobCompanyName ?? '', a.jobTitle ?? '', this.stageLabel(a.status),
      a.matchScore != null ? String(a.matchScore) : '',
      a.createdAt ?? '', a.appliedAt ?? '', a.updatedAt ?? '',
      a.notes ?? '',
    ]);
    const csv = [header, ...rows]
      .map(cols => cols.map(v => `"${String(v).replace(/"/g, '""')}"`).join(','))
      .join('\r\n');
    this.downloadBlob(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }),
      `applications-${this.today()}.csv`);
  }

  /** Full JSON bundle: every application plus every generated document. */
  exportJson(): void {
    if (this.exporting()) return;
    this.exporting.set(true);
    this.aiApi.getDocuments().subscribe({
      next: docs => {
        const byJob = new Map<string, unknown[]>();
        for (const doc of docs) {
          const list = byJob.get(doc.jobId) ?? [];
          list.push(doc);
          byJob.set(doc.jobId, list);
        }
        const bundle = {
          exportedAt: new Date().toISOString(),
          applications: this.applications.map(a => ({
            ...a,
            generatedDocuments: byJob.get(a.jobId) ?? [],
          })),
        };
        this.downloadBlob(new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' }),
          `applications-export-${this.today()}.json`);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false)
    });
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
