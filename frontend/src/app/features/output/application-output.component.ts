import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { DiffViewerComponent } from '../../shared/components/diff-viewer/diff-viewer.component';
import { RichTextEditorComponent } from '../resume-builder/shared/rich-text-editor.component';
import { RichTextPipe } from '../resume-builder/shared/rich-text.pipe';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { Application } from '../../core/models/application.model';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { DocumentIdentity, StructuredDocument } from '../../core/models/structured-document.model';

interface LetterTemplate {
  key: string;
  label: string;
  sub: string;
  bg: string;
  fg: string;
  muted: string;
  divider: string;
  font: string;
  size: number;
  lineH: number;
  headerStyle: 'columns' | 'centered' | 'rail';
  rail: boolean;
  swatchBg: string;
  swatchAccent: string;
}

const LETTER_TEMPLATES: LetterTemplate[] = [
  {
    key: 'editorial', label: 'Editorial', sub: 'Calm serif headers - cream paper',
    bg: '#f6f3ec', fg: '#1a1714', muted: '#6b6660', divider: '#d9d2c4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.65,
    headerStyle: 'columns', rail: false, swatchBg: '#f6f3ec', swatchAccent: '#1a1714',
  },
  {
    key: 'classic', label: 'Classic', sub: 'Serif body - centered header',
    bg: '#fbfaf6', fg: '#1c1a16', muted: '#5a554e', divider: '#cfc8b9',
    font: '"Source Serif Pro", Charter, Cambria, Georgia, serif', size: 13, lineH: 1.78,
    headerStyle: 'centered', rail: false, swatchBg: '#fbfaf6', swatchAccent: '#5a554e',
  },
  {
    key: 'bold', label: 'Bold', sub: 'White paper - amber rail accent',
    bg: '#ffffff', fg: '#0e0e0e', muted: '#5a5a5a', divider: '#e5e0d4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.62,
    headerStyle: 'rail', rail: true, swatchBg: '#ffffff', swatchAccent: '#f5a623',
  },
];

type FormatKey = 'app' | 'cl' | 'dm';

const FORMAT_TO_DOC_TYPE: Record<FormatKey, string> = {
  app: 'APPLICATION_TEXT',
  cl: 'COVER_LETTER',
  dm: 'RECRUITER_MESSAGE',
};

@Component({
  selector: 'app-application-output',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, DiffViewerComponent, RichTextEditorComponent, RichTextPipe],
  templateUrl: './application-output.component.html',
})
export class ApplicationOutputComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private aiApi = inject(AiApiService);
  private appsApi = inject(ApplicationsApiService);
  private jobsApi = inject(JobsApiService);

  loading = signal(true);
  loadError = signal('');
  application = signal<Application | null>(null);
  documents = signal<GeneratedDocument[]>([]);

  format = signal<FormatKey>('app');
  template = signal('editorial');
  tplOpen = signal(false);
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
  isRichContent = computed<boolean>(() => (this.activeDoc()?.content ?? '').includes('<'));

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
    if (fmt === 'cl' || fmt === 'dm') this.format.set(fmt);

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

  goBack(): void {
    const appId = this.route.snapshot.paramMap.get('id');
    if (appId) {
      this.router.navigate(['/applications', appId]);
    } else {
      this.router.navigate(['/applications']);
    }
  }
}
