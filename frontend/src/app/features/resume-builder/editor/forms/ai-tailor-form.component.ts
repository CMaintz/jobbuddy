import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { structuredDocToResumeData } from '../../services/structured-doc-mapper';
import { AiApiService } from '../../../../core/api/ai.api';
import { JobsApiService } from '../../../../core/api/jobs.api';
import { PromptApiService } from '../../../../core/api/prompt.api';
import { AtsReport, StructuredDocument } from '../../../../core/models/structured-document.model';
import { PromptTemplate } from '../../../../core/models/prompt-template.model';
import { ResumeData } from '../../models/resume-builder.models';
import { DiffViewerComponent } from '../../../../shared/components/diff-viewer/diff-viewer.component';
import { JbButtonComponent } from '../../../../shared/components/jb-button/jb-button.component';

const TAILOR_PROMPTS = [
  'tailoredCv.tailor.depth',
  'tailoredCv.tailor.craft',
  'tailoredCv.tailor.quantify',
  'tailoredCv.tailor.shorter',
];

/**
 * AI panel for job-linked drafts: shows the ATS report of the latest
 * generation and re-tailors the whole CV — new content lands in this draft
 * (after a diff review), the chosen template/layout stays as-is.
 */
@Component({
  selector: 'app-ai-tailor-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, DiffViewerComponent, JbButtonComponent],
  templateUrl: './ai-tailor-form.component.html',
})
export class AiTailorFormComponent implements OnInit {
  protected state = inject(ResumeStateService);
  private translate = inject(TranslateService);
  private aiApi = inject(AiApiService);
  private jobsApi = inject(JobsApiService);
  private promptApi = inject(PromptApiService);

  atsReport = signal<AtsReport | null>(null);
  generating = signal(false);
  error = signal('');
  promptTemplates: PromptTemplate[] = [];
  selectedPromptId: string | null = null;
  instructions = '';
  tailorPrompts = TAILOR_PROMPTS;

  /** Regenerated content awaiting the user's diff review. */
  pending = signal<{ data: ResumeData; ats: AtsReport | null } | null>(null);
  summaryBefore = signal('');
  summaryAfter = signal('');

  ngOnInit(): void {
    this.loadAtsReport();
    this.promptApi.getAll().subscribe({
      next: templates => this.promptTemplates = templates.filter(t =>
        !t.category || t.category === 'GENERAL' || t.category === 'CV_TAILORING'),
      error: () => {}
    });
  }

  /** ATS data lives on the latest generated CV document for this job. */
  private loadAtsReport(): void {
    const jobId = this.state.draftJobId();
    if (!jobId) return;
    this.jobsApi.getDocumentsForJob(jobId).subscribe({
      next: docs => {
        const latestCv = docs
          .filter(d => d.documentType === 'CV' && d.structuredContent)
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];
        if (!latestCv) return;
        try {
          const doc = JSON.parse(latestCv.structuredContent!) as StructuredDocument;
          this.atsReport.set(doc.atsReport ?? null);
        } catch { /* stale/unparseable content — panel just hides the report */ }
      },
      error: () => {}
    });
  }

  addTailorPrompt(promptOrKey: string): void {
    const prompt = this.translate.instant(promptOrKey);
    this.instructions = this.instructions
      ? `${this.instructions.trim().replace(/\.?$/, '.')} ${prompt}.`
      : `${prompt}.`;
  }

  regenerate(): void {
    const jobId = this.state.draftJobId();
    if (!jobId || this.generating()) return;
    this.generating.set(true);
    this.error.set('');
    this.aiApi.generateStructuredCv({
      jobId,
      promptTemplateId: this.selectedPromptId ?? undefined,
      customInstructions: this.instructions.trim() || undefined,
      showProfileImage: true,
    }).subscribe({
      next: doc => {
        this.generating.set(false);
        const data = structuredDocToResumeData(doc);
        this.summaryBefore.set(this.state.personalInfo().summary ?? '');
        this.summaryAfter.set(data.personalInfo.summary ?? '');
        this.pending.set({ data, ats: doc.atsReport ?? null });
      },
      error: err => {
        this.generating.set(false);
        this.error.set(err?.error?.message ?? this.translate.instant('resumeBuilder.aiTailor.genFailed'));
      }
    });
  }

  /** Swap in the regenerated content; template/layout settings are untouched. */
  applyPending(): void {
    const pending = this.pending();
    if (!pending) return;
    this.state.replaceResumeData(pending.data);
    if (pending.ats) this.atsReport.set(pending.ats);
    this.pending.set(null);
    this.instructions = '';
  }

  discardPending(): void {
    this.pending.set(null);
  }
}
