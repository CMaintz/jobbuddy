import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { Application } from '../../core/models/application.model';

const TYPE_LABELS: Record<string, string> = {
  CV: 'Tailored CV',
  COVER_LETTER: 'Cover letter',
  APPLICATION_TEXT: 'Application',
  RECRUITER_MESSAGE: 'Short pitch',
  FOLLOW_UP_MESSAGE: 'Follow-up',
  CV_ANALYSIS_REPORT: 'CV analysis',
};

const TYPE_TONES: Record<string, 'accent' | 'info' | 'violet' | 'success' | 'neutral'> = {
  CV: 'violet',
  COVER_LETTER: 'info',
  APPLICATION_TEXT: 'accent',
  RECRUITER_MESSAGE: 'success',
  FOLLOW_UP_MESSAGE: 'neutral',
  CV_ANALYSIS_REPORT: 'neutral',
};

interface DocRow {
  doc: GeneratedDocument;
  company: string;
  role: string;
  applicationId?: string;
}

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [CommonModule, JbIconComponent, JbTopbarComponent, JbPillComponent],
  templateUrl: './documents.component.html'
})
export class DocumentsComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private router = inject(Router);

  loading = signal(true);
  loadError = signal(false);
  activeType = signal('all');
  rows = signal<DocRow[]>([]);

  typeFilters = Object.entries(TYPE_LABELS).map(([key, label]) => ({ key, label }));

  typeLabel = (t: string) => TYPE_LABELS[t] ?? t;
  typeTone = (t: string) => TYPE_TONES[t] ?? 'neutral';

  ngOnInit(): void {
    forkJoin({
      docs: this.aiApi.getDocuments(),
      apps: this.appsApi.getAll(),
    }).subscribe({
      next: ({ docs, apps }) => {
        const byJob = new Map<string, Application>(apps.filter(a => a.jobId).map(a => [a.jobId, a]));
        const byId = new Map<string, Application>(apps.map(a => [a.id, a]));
        this.rows.set((docs as GeneratedDocument[])
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
          .map(doc => {
            const app = (doc.applicationId ? byId.get(doc.applicationId) : undefined)
              ?? (doc.jobId ? byJob.get(doc.jobId) : undefined);
            return {
              doc,
              company: app?.jobCompanyName ?? '—',
              role: app?.jobTitle ?? '',
              applicationId: app?.id,
            };
          }));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadError.set(true);
      }
    });
  }

  filteredRows(): DocRow[] {
    const type = this.activeType();
    if (type === 'all') return this.rows();
    return this.rows().filter(r => r.doc.documentType === type);
  }

  presentTypes(): { key: string; label: string }[] {
    const present = new Set(this.rows().map(r => r.doc.documentType));
    return this.typeFilters.filter(t => present.has(t.key));
  }

  /** CVs open the CV flow (config + builder); letter-type docs open the output screen. */
  open(row: DocRow): void {
    if (row.doc.documentType === 'CV_ANALYSIS_REPORT') {
      this.router.navigate(['/analysis']);
      return;
    }
    if (row.applicationId) {
      if (row.doc.documentType === 'CV') {
        this.router.navigate(['/applications', row.applicationId, 'cv']);
        return;
      }
      const format = row.doc.documentType === 'COVER_LETTER' ? 'cl'
        : row.doc.documentType === 'RECRUITER_MESSAGE' ? 'dm'
        : row.doc.documentType === 'FOLLOW_UP_MESSAGE' ? 'fu' : 'app';
      this.router.navigate(['/applications', row.applicationId, 'output'], { queryParams: { format } });
    } else if (row.doc.jobId) {
      this.router.navigate(['/jobs', row.doc.jobId]);
    }
  }

  ageLabel(iso: string): string {
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
    if (days === 0) return 'today';
    if (days < 7) return `${days}d ago`;
    if (days < 30) return `${Math.floor(days / 7)}w ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }

  preview(content: string): string {
    return (content ?? '').replace(/\s+/g, ' ').slice(0, 180);
  }
}
