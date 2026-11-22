import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent, PILL_CLASS, PillTone } from '../../shared/components/jb-pill/jb-pill.component';
import { StatusChipComponent } from '../../shared/components/status-chip/status-chip.component';
import { StatusPickerComponent } from '../../shared/components/status-picker/status-picker.component';
import { NotifyService } from '../../shared/services/notify.service';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';

type KanbanColumnKey = 'saved' | 'applied' | 'inProgress' | 'offer' | 'closed';

/**
 * The board groups statuses into a few columns so it stays readable; a card in a column that
 * holds several statuses shows its own. A Record over every status, so a status added later fails
 * to compile here instead of silently vanishing from the board. PREPARING sits under saved
 * because the apply flow starts drafts there, and to the user a draft is a job not sent yet.
 */
const KANBAN_COLUMN_OF: Record<ApplicationStatus, KanbanColumnKey> = {
  SAVED: 'saved', PREPARING: 'saved',
  APPLIED: 'applied',
  RECRUITER_CONTACT: 'inProgress', INTERVIEW: 'inProgress',
  TECHNICAL_TEST: 'inProgress', FINAL_ROUND: 'inProgress',
  OFFER: 'offer',
  REJECTED: 'closed', ARCHIVED: 'closed',
};

interface KanbanColumn {
  key: KanbanColumnKey;
  label: string;
  tone: PillTone;
  /** True when the column holds more than one status, so each card must name its own. */
  grouped: boolean;
}

function kanbanColumn(key: KanbanColumnKey, label: string, tone: PillTone): KanbanColumn {
  const statuses = Object.values(KANBAN_COLUMN_OF).filter(column => column === key);
  return { key, label, tone, grouped: statuses.length > 1 };
}

interface StageConfig {
  key: ApplicationStatus;
  label: string;
  tone: 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet';
}

@Component({
  selector: 'app-pipeline',
  imports: [
    FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent,
    JbPillComponent, StatusPickerComponent, StatusChipComponent, CompanyMarkComponent, FitBarComponent,
    JbTopbarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './pipeline.component.html'
})
export class PipelineComponent implements OnInit {
  private api = inject(ApplicationsApiService);
  private translate = inject(TranslateService);
  private router = inject(Router);
  private notify = inject(NotifyService);

  applications: Application[] = [];
  loading = true;
  view = signal<'table' | 'kanban'>('table');
  activeFilters = signal<Set<ApplicationStatus>>(new Set());
  search = signal('');
  protected readonly pillClass = PILL_CLASS;

  stages: StageConfig[] = [
    { key: 'SAVED', label: 'pipeline.stage.saved', tone: 'neutral' },
    { key: 'APPLIED', label: 'pipeline.stage.applied', tone: 'info' },
    { key: 'RECRUITER_CONTACT', label: 'pipeline.stage.screen', tone: 'violet' },
    { key: 'INTERVIEW', label: 'pipeline.stage.interview', tone: 'accent' },
    { key: 'OFFER', label: 'pipeline.stage.offer', tone: 'success' },
    { key: 'REJECTED', label: 'pipeline.stage.rejected', tone: 'danger' },
  ];

  kanbanColumns: KanbanColumn[] = [
    kanbanColumn('saved', 'pipeline.stage.saved', 'neutral'),
    kanbanColumn('applied', 'pipeline.stage.applied', 'info'),
    kanbanColumn('inProgress', 'pipeline.column.inProgress', 'accent'),
    kanbanColumn('offer', 'pipeline.stage.offer', 'success'),
    kanbanColumn('closed', 'pipeline.column.closed', 'neutral'),
  ];

  ngOnInit(): void {
    this.api.getAll().subscribe({
      next: (apps) => { this.applications = apps; this.loading = false; },
      error: () => this.loading = false
    });
  }

  open(app: Application): void {
    this.router.navigate(['/applications', app.id]);
  }

  /** Rows with a status move waiting on the server. */
  pendingIds = signal<ReadonlySet<string>>(new Set());

  /** On failure the row keeps its old status, so what is shown is what the server holds. */
  changeStatus(app: Application, status: ApplicationStatus): void {
    this.setPending(app.id, true);
    this.api.updateStatus(app.id, status).subscribe({
      next: updated => {
        this.applications = this.applications.map(a => a.id === updated.id ? updated : a);
        this.setPending(app.id, false);
      },
      error: () => {
        this.setPending(app.id, false);
        this.notify.error('notify.actionFailed');
      },
    });
  }

  private setPending(id: string, pending: boolean): void {
    const ids = new Set(this.pendingIds());
    if (pending) ids.add(id);
    else ids.delete(id);
    this.pendingIds.set(ids);
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

  appsForColumn(column: KanbanColumnKey): Application[] {
    return this.searchedApplications().filter(a => KANBAN_COLUMN_OF[a.status] === column);
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
