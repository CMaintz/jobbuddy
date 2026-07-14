import { Component, ViewChild, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbDropdownComponent } from '../../shared/components/jb-dropdown/jb-dropdown.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { LetterPaperComponent } from './letter-paper.component';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { PdfExportService } from '../resume-builder/services/pdf-export.service';
import { AtsPdfService } from '../../shared/services/ats-pdf.service';
import { Application } from '../../core/models/application.model';
import { Job } from '../../core/models/job.model';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { DocumentIdentity, StructuredDocument } from '../../core/models/structured-document.model';

import { FORMAT_TO_DOC_TYPE, FormatKey, LETTER_TEMPLATES, LetterTemplate, WORD_TARGETS } from './letter-templates';

@Component({
  selector: 'app-application-output',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbDropdownComponent, DiffViewerComponent, LetterPaperComponent],
  templateUrl: './application-output.component.html',
})
export class ApplicationOutputComponent implements OnInit {
  @ViewChild(LetterPaperComponent) paper?: LetterPaperComponent;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);
  private pdfExport = inject(PdfExportService);
  private atsPdf = inject(AtsPdfService);

  loading = signal(true);
  downloading = signal(false);
  loadError = signal('');
  application = signal<Application | null>(null);
  documents = signal<GeneratedDocument[]>([]);
  /** The job this application targets — description feeds refinements, keywords feed the stats. */
  job = signal<Job | null>(null);
  /** Highlight JD keywords inside the rendered letter. */
  highlightKw = signal(false);

  private jobDescription = computed<string | null>(() => this.job()?.descriptionClean ?? null);

  format = signal<FormatKey>('app');
  template = signal('editorial');
  refining = signal(false);
  generatingMissing = signal(false);
  copied = signal(false);
  editing = signal(false);
  editDirty = signal(false);
  savingEdit = signal(false);
  originalText = signal('');
  refinedText = signal('');
  customRevisePrompt = '';
  mobileShowSidebar = signal(false);

  templates = LETTER_TEMPLATES;
  formats: { key: FormatKey; label: string }[] = [
    { key: 'app', label: 'Application' },
    { key: 'cl', label: 'Cover letter' },
    { key: 'dm', label: 'Short pitch' },
    { key: 'fu', label: 'Follow-up' },
  ];

  revisePrompts = [
    'Make the second paragraph tighter',
    'Sound less like a job ad',
    'Shorter by 50 words',
    'More concrete, fewer adjectives',
  ];

  /** Latest document per format, newest first. */
  activeDoc = computed<GeneratedDocument | null>(() => {
    const wanted = FORMAT_TO_DOC_TYPE[this.format()];
    return this.documents().find(d => d.documentType === wanted) ?? null;
  });

  /** Whether an tailored CV already exists for this job (for the next-step link). */
  hasCv = computed<boolean>(() => this.documents().some(d => d.documentType === 'CV'));

  /** Keywords the JD cares about (technologies + skills + AI tags). */
  jdKeywords = computed<string[]>(() => {
    const job = this.job();
    if (!job) return [];
    const all = [...(job.technologies ?? []), ...(job.skills ?? []), ...(job.aiTags ?? [])]
      .map(k => k.trim()).filter(k => k.length > 1);
    return [...new Set(all)].slice(0, 15);
  });

  /** Which JD keywords the current letter actually mentions. */
  keywordStats = computed<{ matched: string[]; missing: string[] } | null>(() => {
    const keywords = this.jdKeywords();
    if (keywords.length === 0 || !this.activeDoc()) return null;
    const text = this.plainText().toLowerCase();
    const matched: string[] = [];
    const missing: string[] = [];
    for (const kw of keywords) (text.includes(kw.toLowerCase()) ? matched : missing).push(kw);
    return { matched, missing };
  });

  /** Length guidance for the current format. */
  wordHint = computed<string>(() => {
    if (!this.activeDoc()) return '';
    const [lo, hi] = WORD_TARGETS[this.format()];
    const n = this.wordCount();
    if (n < lo) return `On the short side — ${lo}–${hi} words usually lands better here.`;
    if (n > hi) return `Running long — ${lo}–${hi} words is the sweet spot for this format.`;
    return `Good length — within the ${lo}–${hi} word sweet spot.`;
  });

  /** Identity from the structured payload of the active document, if present. */
  identity = computed<DocumentIdentity>(() => {
    const doc = this.activeDoc();
    if (doc?.structuredContent) {
      try {
        const parsed = JSON.parse(doc.structuredContent) as StructuredDocument;
        return parsed.identity ?? {};
      } catch { /* fall through */ }
    }
    return {};
  });

  /** True when the content carries rich-text markup (from in-place editing). */

  paragraphs = computed<string[]>(() => {
    const content = this.activeDoc()?.content ?? '';
    return content.split(/\n{2,}/).map(p => p.trim()).filter(Boolean);
  });

  wordCount = computed<number>(() => {
    const content = this.plainText();
    return content.trim() ? content.trim().split(/\s+/).length : 0;
  });

  /** Plain-text version of the active document (tags stripped). */
  plainText(): string {
    const content = this.activeDoc()?.content ?? '';
    if (!content.includes('<')) return content;
    const div = document.createElement('div');
    div.innerHTML = content.replace(/<\/(p|li|ul|ol)>/g, '</$1>\n');
    return (div.textContent ?? '').replace(/\n{3,}/g, '\n\n').trim();
  }

  docCountForFormat(key: FormatKey): number {
    return this.documents().filter(d => d.documentType === FORMAT_TO_DOC_TYPE[key]).length;
  }

  activeTemplate(): LetterTemplate {
    return this.templates.find(t => t.key === this.template()) ?? this.templates[0];
  }

  formatLabel(): string {
    return this.formats.find(f => f.key === this.format())?.label ?? 'Application';
  }

  readingTime(): string {
    const minutes = this.wordCount() / 200;
    const mm = Math.floor(minutes);
    const ss = Math.round((minutes - mm) * 60);
    return `${mm}:${ss.toString().padStart(2, '0')}`;
  }

  ageLabel(iso?: string): string {
    if (!iso) return '';
    const diffMs = Date.now() - new Date(iso).getTime();
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    return `${Math.floor(hours / 24)}d ago`;
  }

  stats() {
    return [
      { label: 'Words', value: `${this.wordCount()}` },
      { label: 'Reading', value: this.readingTime() },
      { label: 'Versions', value: `${this.docCountForFormat(this.format())}` },
      { label: 'Model', value: this.activeDoc()?.modelUsed?.split('-').slice(0, 2).join('-') ?? '—' },
    ];
  }

  ngOnInit(): void {
    const fmt = this.route.snapshot.queryParamMap.get('format');
    if (fmt === 'cl' || fmt === 'dm' || fmt === 'fu') this.format.set(fmt);

    const appId = this.route.snapshot.paramMap.get('id');
    if (!appId) {
      this.loadError.set('No application selected.');
      this.loading.set(false);
      return;
    }
    this.appsApi.getById(appId).subscribe({
      next: app => {
        this.application.set(app);
        this.loadDocuments(app.jobId);
        this.jobsApi.getById(app.jobId).subscribe({
          next: job => this.job.set(job),
          error: () => {}
        });
      },
      error: () => {
        this.loadError.set('Could not load this application.');
        this.loading.set(false);
      }
    });
  }

  private loadDocuments(jobId: string): void {
    this.jobsApi.getDocumentsForJob(jobId).subscribe({
      next: docs => {
        // newest first so activeDoc picks the latest version per type
        this.documents.set([...docs].sort((a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()));
        // If the requested format has no document but another does, switch to it
        if (!this.activeDoc()) {
          const withDoc = this.formats.find(f => this.docCountForFormat(f.key) > 0);
          if (withDoc) this.format.set(withDoc.key);
        }
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load the generated documents.');
        this.loading.set(false);
      }
    });
  }

  /** Generate the currently selected format if it doesn't exist yet. */
  generateMissing(): void {
    const app = this.application();
    if (!app || this.generatingMissing()) return;
    this.generatingMissing.set(true);
    this.aiApi.generateDocument({
      jobId: app.jobId,
      documentType: FORMAT_TO_DOC_TYPE[this.format()],
    }).subscribe({
      next: () => {
        this.generatingMissing.set(false);
        this.loadDocuments(app.jobId);
      },
      error: () => this.generatingMissing.set(false)
    });
  }

  copyContent(): void {
    const content = this.plainText();
    if (!content) return;
    navigator.clipboard.writeText(content).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1800);
    });
  }

  /** In-place editing of the letter body. */
  onEdited(html: string): void {
    const doc = this.activeDoc();
    if (!doc) return;
    this.documents.update(docs => docs.map(d =>
      d.id === doc.id ? { ...d, content: html } : d));
    this.editDirty.set(true);
  }

  saveEdit(): void {
    const doc = this.activeDoc();
    const app = this.application();
    if (!doc || this.savingEdit()) return;
    this.savingEdit.set(true);
    const structured = this.buildStructuredDoc(doc, doc.content);
    this.aiApi.saveStructuredDocument(structured, app?.jobId).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editDirty.set(false);
        this.editing.set(false);
        if (app) this.loadDocuments(app.jobId);
      },
      error: () => this.savingEdit.set(false)
    });
  }

  refineWith(prompt: string): void {
    const doc = this.activeDoc();
    if (!prompt?.trim() || this.refining() || !doc?.content) return;
    this.originalText.set(this.plainText());
    this.refining.set(true);

    this.aiApi.refine({
      currentContent: this.plainText(),
      userMessage: prompt,
      jobDescription: this.jobDescription() ?? undefined,
    }).subscribe({
      next: (resp) => {
        this.refinedText.set(resp.refinedContent);
        this.refining.set(false);
      },
      error: () => {
        this.refining.set(false);
      }
    });
  }

  acceptRefinement(): void {
    const refined = this.refinedText();
    const doc = this.activeDoc();
    const app = this.application();
    if (!refined || !doc) return;

    // Update locally so the paper reflects the accepted text immediately
    this.documents.update(docs => docs.map(d =>
      d.id === doc.id ? { ...d, content: refined } : d));
    this.originalText.set('');
    this.refinedText.set('');

    // Persist as a new manual-edit version
    const structured = this.buildStructuredDoc(doc, refined);
    this.aiApi.saveStructuredDocument(structured, app?.jobId).subscribe({
      next: () => app && this.loadDocuments(app.jobId),
      error: () => { /* local state already updated; next generation will still work */ }
    });
  }

  private buildStructuredDoc(doc: GeneratedDocument, body: string): StructuredDocument {
    if (doc.structuredContent) {
      try {
        const parsed = JSON.parse(doc.structuredContent) as StructuredDocument;
        return { ...parsed, bodyContent: body };
      } catch { /* fall through */ }
    }
    return {
      documentType: (doc.documentType as StructuredDocument['documentType']) ?? 'APPLICATION_TEXT',
      exportMode: 'ATS',
      templateId: doc.templateId ?? 'application-ats',
      identity: this.identity(),
      sections: [],
      bodyContent: body,
    };
  }

  /** Pixel-perfect capture of the styled letter (image-based). */
  downloadPdf(): void {
    const paperEl = this.paper?.el.nativeElement;
    if (!paperEl || this.downloading() || this.editing()) return;
    this.downloading.set(true);
    this.pdfExport.download(paperEl, this.exportFilename())
      .finally(() => this.downloading.set(false));
  }

  /** Text-based letter PDF — selectable text, safe for ATS parsers. */
  downloadAtsPdf(): void {
    if (!this.activeDoc() || this.downloading()) return;
    this.downloading.set(true);
    const id = this.identity();
    this.atsPdf.downloadLetter({
      name: id.name,
      headline: id.headline,
      contactLine: [id.email, id.phone, id.location].filter(Boolean).join('  ·  ') || undefined,
      paragraphs: this.plainText().split(/\n{2,}/).map(p => p.trim()).filter(Boolean),
      filename: `${this.exportFilename()} (ATS)`,
    }).finally(() => this.downloading.set(false));
  }

  private exportFilename(): string {
    const app = this.application();
    return app ? `${app.jobCompanyName ?? 'application'} - ${this.formatLabel()}` : this.formatLabel();
  }

  goToCv(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) this.router.navigate(['/applications', appId, 'cv']);
  }

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }
}
