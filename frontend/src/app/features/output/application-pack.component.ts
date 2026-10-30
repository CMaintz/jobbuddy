import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { GeneratedDocument } from '../../core/models/generated-document.model';

import { NotifyService } from '../../shared/services/notify.service';
/**
 * Combined "application pack" review shown after Quick apply: the tailored CV and the cover
 * letter side by side, each with a preview and a link into its full editor.
 */
@Component({
  selector: 'app-application-pack',
  imports: [RouterLink, TranslateModule, JbTopbarComponent, JbButtonComponent],
  host: { class: 'flex flex-col h-full min-h-0' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './application-pack.component.html'
})
export class ApplicationPackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  protected notify = inject(NotifyService);
  private jobsApi = inject(JobsApiService);

  appId = '';
  jobId = '';
  loading = signal(true);
  cv = signal<GeneratedDocument | null>(null);
  cover = signal<GeneratedDocument | null>(null);

  ngOnInit(): void {
    this.appId = this.route.snapshot.paramMap.get('id') ?? '';
    this.jobId = this.route.snapshot.queryParamMap.get('jobId') ?? '';
    if (!this.jobId) { this.loading.set(false); return; }

    this.jobsApi.getDocumentsForJob(this.jobId).subscribe({
      next: docs => {
        const latest = (types: string[]) => docs
          .filter(d => types.includes(d.documentType) && (d.content?.trim() || d.structuredContent))
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0] ?? null;
        this.cv.set(latest(['CV']));
        this.cover.set(latest(['COVER_LETTER', 'UNSOLICITED_APPLICATION', 'APPLICATION_TEXT']));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  preview(doc: GeneratedDocument | null): string {
    const text = doc?.content?.trim() ?? '';
    return text.length > 900 ? text.slice(0, 900) + '…' : text;
  }

  copy(doc: GeneratedDocument | null): void {
    if (!doc?.content || !navigator.clipboard) return;
    navigator.clipboard.writeText(doc.content).then(
      () => this.notify.success('pack.copied'),
      () => {});
  }
}
