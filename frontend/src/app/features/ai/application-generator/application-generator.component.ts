import { Component, OnInit, inject, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AiApiService } from '../../../core/api/ai.api';
import { DocumentApiService } from '../../../core/api/document.api';
import { PromptApiService } from '../../../core/api/prompt.api';
import { JobsApiService } from '../../../core/api/jobs.api';
import { PdfTemplatesApiService } from '../../../core/api/pdf-templates.api';
import { CvVersion } from '../../../core/models/cv-version.model';
import { PromptTemplate } from '../../../core/models/prompt-template.model';
import { PdfTemplate } from '../../../core/models/pdf-template.model';
import { Job } from '../../../core/models/job.model';

interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

const LANGUAGES = [
  { code: 'Danish', label: 'Danish' },
  { code: 'English', label: 'English' },
  { code: 'Swedish', label: 'Swedish' },
  { code: 'Norwegian', label: 'Norwegian' },
  { code: 'German', label: 'German' },
];

@Component({
  selector: 'app-application-generator',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  styles: [`
    .editor {
      min-height: 300px;
      outline: none;
      line-height: 1.75;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .editor:empty:before {
      content: attr(data-placeholder);
      color: #9ca3af;
      pointer-events: none;
    }
    @media print {
      body * { visibility: hidden; }
      .print-area, .print-area * { visibility: visible; }
      .print-area {
        position: fixed; top: 0; left: 0;
        width: 210mm; padding: 20mm;
        font-family: Georgia, serif;
        font-size: 11pt;
        line-height: 1.6;
        color: #111;
      }
    }
  `],
  template: `
    <div class="space-y-6 max-w-5xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">Generate Application</h1>

      <!-- Configuration panel -->
      <div class="card">
        <form [formGroup]="form" (ngSubmit)="generate()" class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="label">Document Type</label>
            <select formControlName="documentType" class="input">
              <option value="COVER_LETTER">Cover Letter</option>
              <option value="APPLICATION_TEXT">Application Text</option>
              <option value="RECRUITER_MESSAGE">Recruiter Message</option>
              <option value="FOLLOW_UP_MESSAGE">Follow-up Message</option>
              <option value="CV">Angled CV</option>
            </select>
          </div>

          <div>
            <label class="label">Language</label>
            <select formControlName="targetLanguage" class="input">
              @for (lang of languages; track lang.code) {
                <option [value]="lang.code">{{ lang.label }}</option>
              }
            </select>
          </div>

          <div>
            <label class="label">CV Version</label>
            <select formControlName="cvVersionId" class="input">
              <option value="">— select a CV —</option>
              @for (cv of cvVersions; track cv.id) {
                <option [value]="cv.id">{{ cv.name }} (v{{ cv.versionNumber }})</option>
              }
            </select>
          </div>

          <div>
            <label class="label">Prompt Template (optional)</label>
            <select formControlName="promptTemplateId" class="input">
              <option value="">— default prompt —</option>
              @for (t of promptTemplates; track t.id) {
                <option [value]="t.id">{{ t.name }}</option>
              }
            </select>
          </div>

          <div>
            <label class="label">PDF Template (optional)</label>
            <select formControlName="pdfTemplateId" class="input">
              <option value="">— no template —</option>
              @for (t of pdfTemplates; track t.id) {
                <option [value]="t.id">{{ t.name }}{{ t.isSystem ? ' (system)' : '' }}</option>
              }
            </select>
          </div>

          <div class="md:col-span-2">
            <label class="label">Custom Instructions (optional)</label>
            <textarea formControlName="customInstructions" class="input" rows="2"
                      placeholder="e.g. Keep it under 300 words, emphasise leadership experience..."></textarea>
          </div>

          <div class="md:col-span-2 flex items-center gap-3">
            <label class="flex items-center gap-2 cursor-pointer">
              <input type="checkbox" formControlName="useStyleFromHistory" class="rounded border-gray-300" />
              <span class="text-sm text-gray-700">
                Match style from my past applications
              </span>
            </label>
            <span class="text-xs text-gray-400">Uses your 3 most recent documents of this type as style examples.</span>
          </div>

          @if (jobTitle) {
            <div class="md:col-span-2 text-sm text-gray-500 flex items-center gap-2">
              <span class="text-green-600">✓</span>
              Generating for: <span class="font-medium text-gray-700">{{ jobTitle }}</span>
            </div>
          }

          <div class="md:col-span-2">
            <button type="submit" [disabled]="form.invalid || loading" class="btn-primary w-full">
              {{ loading ? 'Generating...' : 'Generate' }}
            </button>
          </div>
        </form>
      </div>

      @if (error) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ error }}</div>
      }

      <!-- Editor + Chat panel (shown after generation) -->
      @if (showEditor) {
        <div class="grid grid-cols-1 lg:grid-cols-5 gap-4">

          <!-- Rich Text Editor -->
          <div class="lg:col-span-3 card space-y-3 print-area">
            <div class="flex items-center justify-between">
              <h2 class="text-base font-semibold text-gray-900">Document</h2>
              <div class="flex gap-2">
                <button (click)="copyContent()" class="btn-secondary text-xs">
                  {{ copied ? 'Copied!' : 'Copy' }}
                </button>
                <button (click)="printDocument()" class="btn-secondary text-xs">
                  Download PDF
                </button>
              </div>
            </div>

            <!-- contenteditable rich text editor -->
            <div #editorEl
                 class="editor border border-gray-200 rounded-lg p-4 text-sm text-gray-800 bg-white"
                 contenteditable="true"
                 data-placeholder="Generated content will appear here..."
                 (input)="onEditorInput($event)"
                 [innerHTML]="editorHtml">
            </div>

            @if (result) {
              <div class="text-xs text-gray-400 text-right">
                Model: {{ result.modelUsed }}
                @if (result.tokensUsed) { &bull; {{ result.tokensUsed }} tokens }
              </div>
            }
          </div>

          <!-- AI Chat Panel -->
          <div class="lg:col-span-2 card flex flex-col gap-3" style="height: fit-content; max-height: 600px;">
            <h2 class="text-base font-semibold text-gray-900 shrink-0">Refine with AI</h2>
            <p class="text-xs text-gray-500 shrink-0">Ask the AI to adjust the document — e.g. "Make it shorter", "Add more emphasis on leadership"</p>

            <!-- Chat history -->
            <div #chatScroll class="flex-1 space-y-2 overflow-y-auto min-h-0" style="max-height: 300px;">
              @for (msg of chatHistory; track $index) {
                <div [class]="msg.role === 'user'
                  ? 'flex justify-end'
                  : 'flex justify-start'">
                  <div [class]="msg.role === 'user'
                    ? 'bg-blue-600 text-white text-xs rounded-2xl rounded-tr-sm px-3 py-2 max-w-xs'
                    : 'bg-gray-100 text-gray-700 text-xs rounded-2xl rounded-tl-sm px-3 py-2 max-w-xs'">
                    {{ msg.text }}
                  </div>
                </div>
              }
              @if (refining) {
                <div class="flex justify-start">
                  <div class="bg-gray-100 text-gray-500 text-xs rounded-2xl rounded-tl-sm px-3 py-2">
                    Thinking...
                  </div>
                </div>
              }
            </div>

            <!-- Chat input -->
            <div class="flex gap-2 shrink-0">
              <input #chatInput type="text" [(ngModel)]="chatMessage"
                     (keydown.enter)="sendChat()"
                     placeholder="Refine the document..."
                     class="input flex-1 text-sm"
                     [ngModelOptions]="{standalone: true}" />
              <button (click)="sendChat()" [disabled]="!chatMessage.trim() || refining"
                      class="btn-primary text-sm px-3">
                Send
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class ApplicationGeneratorComponent implements OnInit, AfterViewChecked {
  @ViewChild('editorEl') editorEl!: ElementRef<HTMLDivElement>;
  @ViewChild('chatScroll') chatScrollEl!: ElementRef<HTMLDivElement>;
  @ViewChild('chatInput') chatInputEl!: ElementRef<HTMLInputElement>;

  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private aiApi = inject(AiApiService);
  private docApi = inject(DocumentApiService);
  private promptApi = inject(PromptApiService);
  private jobsApi = inject(JobsApiService);
  private pdfApi = inject(PdfTemplatesApiService);

  cvVersions: CvVersion[] = [];
  promptTemplates: PromptTemplate[] = [];
  pdfTemplates: PdfTemplate[] = [];
  languages = LANGUAGES;

  loading = false;
  refining = false;
  error = '';
  result: { content: string; modelUsed: string; tokensUsed?: number } | null = null;

  editorHtml = '';
  currentContent = '';
  showEditor = false;
  copied = false;

  chatHistory: ChatMessage[] = [];
  chatMessage = '';

  jobTitle = '';
  jobDescription = '';

  private shouldScrollChat = false;

  form = this.fb.group({
    documentType: ['COVER_LETTER', Validators.required],
    jobId: [''],
    cvVersionId: [''],
    promptTemplateId: [''],
    pdfTemplateId: [''],
    customInstructions: [''],
    targetLanguage: ['Danish'],
    useStyleFromHistory: [false]
  });

  ngOnInit(): void {
    const jobId = this.route.snapshot.queryParamMap.get('jobId');
    if (jobId) {
      this.form.patchValue({ jobId });
      this.jobsApi.getById(jobId).subscribe(j => {
        this.jobTitle = j.title + ' @ ' + j.companyName;
        this.jobDescription = j.descriptionClean ?? '';
      });
    }
    this.docApi.getCvVersions().subscribe(cvs => this.cvVersions = cvs);
    this.promptApi.getAll().subscribe(ts => this.promptTemplates = ts);
    this.pdfApi.getAll().subscribe(ts => this.pdfTemplates = ts);
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollChat && this.chatScrollEl) {
      const el = this.chatScrollEl.nativeElement;
      el.scrollTop = el.scrollHeight;
      this.shouldScrollChat = false;
    }
  }

  generate(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';

    const v = this.form.value;
    this.aiApi.generate({
      documentType: v.documentType as any,
      jobId: v.jobId || undefined,
      cvVersionId: v.cvVersionId || undefined,
      promptTemplateId: v.promptTemplateId || undefined,
      customInstructions: v.customInstructions || undefined,
      targetLanguage: v.targetLanguage || 'Danish',
      useStyleFromHistory: v.useStyleFromHistory ?? false
    }).subscribe({
      next: r => {
        this.result = r;
        this.setEditorContent(r.content);
        this.showEditor = true;
        this.chatHistory = [];
        this.loading = false;
      },
      error: e => {
        this.error = e.error?.message || 'Generation failed. Please try again.';
        this.loading = false;
      }
    });
  }

  onEditorInput(event: Event): void {
    const el = event.target as HTMLDivElement;
    this.currentContent = el.innerText;
  }

  sendChat(): void {
    if (!this.chatMessage.trim() || this.refining) return;
    const userMsg = this.chatMessage.trim();
    this.chatMessage = '';
    this.chatHistory = [...this.chatHistory, { role: 'user', text: userMsg }];
    this.shouldScrollChat = true;
    this.refining = true;

    const v = this.form.value;
    this.aiApi.refine({
      currentContent: this.currentContent,
      userMessage: userMsg,
      jobDescription: this.jobDescription || undefined,
      targetLanguage: v.targetLanguage || 'Danish'
    }).subscribe({
      next: r => {
        this.setEditorContent(r.refinedContent);
        this.chatHistory = [...this.chatHistory, { role: 'assistant', text: 'Document updated.' }];
        this.shouldScrollChat = true;
        this.refining = false;
      },
      error: () => {
        this.chatHistory = [...this.chatHistory, { role: 'assistant', text: 'Failed to refine. Please try again.' }];
        this.refining = false;
      }
    });
  }

  copyContent(): void {
    navigator.clipboard.writeText(this.currentContent).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }

  printDocument(): void {
    const pdfTemplateId = this.form.value.pdfTemplateId;
    const template = pdfTemplateId ? this.pdfTemplates.find(t => t.id === pdfTemplateId) : null;

    if (template) {
      const content = this.currentContent
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/\n/g, '<br>');
      let html = template.htmlTemplate
        .replace(/\{\{CONTENT\}\}/g, content)
        .replace(/\{\{NAME\}\}/g, '')
        .replace(/\{\{EMAIL\}\}/g, '')
        .replace(/\{\{PHONE\}\}/g, '')
        .replace(/\{\{DATE\}\}/g, new Date().toLocaleDateString('da-DK'));
      const css = template.cssStyles ?? '';
      const win = window.open('', '_blank');
      if (win) {
        win.document.write(`<!DOCTYPE html><html><head><style>${css}</style></head><body>${html}</body></html>`);
        win.document.close();
        win.print();
      }
    } else {
      window.print();
    }
  }

  private setEditorContent(text: string): void {
    this.currentContent = text;
    // Use innerHTML with preserved line breaks for the contenteditable div
    this.editorHtml = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>');
  }
}
