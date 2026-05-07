import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AiApiService } from '../../../core/api/ai.api';
import { DocumentApiService } from '../../../core/api/document.api';
import { PromptApiService } from '../../../core/api/prompt.api';
import { CvVersion } from '../../../core/models/cv-version.model';
import { PromptTemplate } from '../../../core/models/prompt-template.model';

@Component({
  selector: 'app-application-generator',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">Generate Application</h1>

      <div class="card">
        <form [formGroup]="form" (ngSubmit)="generate()" class="space-y-4">
          <div>
            <label class="label">Document Type</label>
            <select formControlName="documentType" class="input">
              <option value="COVER_LETTER">Cover Letter</option>
              <option value="APPLICATION_TEXT">Application Text</option>
              <option value="RECRUITER_MESSAGE">Recruiter Message</option>
              <option value="CV_ANALYSIS_REPORT">CV Analysis Report</option>
            </select>
          </div>

          <div>
            <label class="label">Job ID (optional)</label>
            <input type="text" formControlName="jobId" class="input" placeholder="Job UUID" />
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
            <label class="label">Custom Instructions (optional)</label>
            <textarea formControlName="customInstructions" class="input"
                      placeholder="Additional instructions for the AI..."></textarea>
          </div>

          <button type="submit" [disabled]="form.invalid || loading" class="btn-primary w-full">
            {{ loading ? 'Generating...' : 'Generate' }}
          </button>
        </form>
      </div>

      @if (error) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ error }}</div>
      }

      @if (result) {
        <div class="card">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-base font-semibold text-gray-900">Generated Content</h2>
            <div class="text-xs text-gray-400">
              Model: {{ result.modelUsed }}
              @if (result.tokensUsed) { &bull; {{ result.tokensUsed }} tokens }
            </div>
          </div>
          <div class="bg-gray-50 rounded-lg p-4 text-sm text-gray-700 whitespace-pre-line leading-relaxed font-mono">
            {{ result.content }}
          </div>
          <button (click)="copyContent()" class="btn-secondary text-xs mt-3">
            {{ copied ? 'Copied!' : 'Copy to clipboard' }}
          </button>
        </div>
      }
    </div>
  `
})
export class ApplicationGeneratorComponent implements OnInit {
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private aiApi = inject(AiApiService);
  private docApi = inject(DocumentApiService);
  private promptApi = inject(PromptApiService);

  cvVersions: CvVersion[] = [];
  promptTemplates: PromptTemplate[] = [];
  loading = false;
  error = '';
  result: { content: string; modelUsed: string; tokensUsed?: number } | null = null;
  copied = false;

  form = this.fb.group({
    documentType: ['COVER_LETTER', Validators.required],
    jobId: [''],
    cvVersionId: [''],
    promptTemplateId: [''],
    customInstructions: ['']
  });

  ngOnInit(): void {
    const jobId = this.route.snapshot.queryParamMap.get('jobId');
    if (jobId) this.form.patchValue({ jobId });

    this.docApi.getCvVersions().subscribe(cvs => this.cvVersions = cvs);
    this.promptApi.getAll().subscribe(ts => this.promptTemplates = ts);
  }

  generate(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    this.result = null;

    const v = this.form.value;
    this.aiApi.generate({
      documentType: v.documentType as any,
      jobId: v.jobId || undefined,
      cvVersionId: v.cvVersionId || undefined,
      promptTemplateId: v.promptTemplateId || undefined,
      customInstructions: v.customInstructions || undefined
    }).subscribe({
      next: r => { this.result = r; this.loading = false; },
      error: e => {
        this.error = e.error?.message || 'Generation failed';
        this.loading = false;
      }
    });
  }

  copyContent(): void {
    if (!this.result) return;
    navigator.clipboard.writeText(this.result.content).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }
}
