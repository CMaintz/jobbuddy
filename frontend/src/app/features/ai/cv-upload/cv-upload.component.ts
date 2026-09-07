import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DocumentApiService } from '../../../core/api/document.api';
import { AiApiService } from '../../../core/api/ai.api';
import { CvVersion } from '../../../core/models/cv-version.model';
import { FormActionsComponent } from '../../../shared/components/ui/form-actions.component';
import { runAction } from '../../../shared/utils/async-ui';

@Component({
  selector: 'app-cv-upload',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormActionsComponent],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">CV Management</h1>

      <!-- Existing CVs -->
      @if (cvVersions.length > 0) {
        <div class="card">
          <h2 class="text-base font-semibold text-gray-900 mb-3">Your CVs</h2>
          <div class="space-y-2">
            @for (cv of cvVersions; track cv.id) {
              <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div>
                  <span class="font-medium text-gray-800">{{ cv.name }}</span>
                  @if (cv.isPrimary) {
                    <span class="ml-2 text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">Primary</span>
                  }
                  <div class="text-xs text-gray-400">v{{ cv.versionNumber }} &bull; {{ cv.createdAt | date:'mediumDate' }}</div>
                </div>
                <button (click)="analyze(cv)"
                        [disabled]="analyzing === cv.id"
                        class="btn-secondary text-xs">
                  {{ analyzing === cv.id ? 'Analyzing...' : 'Analyze' }}
                </button>
              </div>
            }
          </div>
        </div>
      }

      <!-- Upload new -->
      <div class="card">
        <h2 class="text-base font-semibold text-gray-900 mb-4">Upload New CV</h2>
        @if (success) {
          <div class="bg-green-50 text-green-700 rounded-md p-3 mb-4 text-sm">CV uploaded successfully!</div>
        }
        @if (error) {
          <div class="bg-red-50 text-red-700 rounded-md p-3 mb-4 text-sm">{{ error }}</div>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" class="space-y-4">
          <div>
            <label class="label">Name</label>
            <input type="text" formControlName="name" class="input" placeholder="e.g. Senior Dev CV 2024" />
          </div>
          <div>
            <label class="label">CV Content (Markdown or plain text)</label>
            <textarea formControlName="content" class="input min-h-48 font-mono text-xs"
                      placeholder="# Your Name&#10;...paste your CV content here..."></textarea>
          </div>
          <app-form-actions
            [showCancel]="false"
            [disabled]="form.invalid || loading"
            [saveLabel]="loading ? 'Uploading...' : 'Upload CV'">
          </app-form-actions>
        </form>
      </div>

      @if (analysisResult) {
        <div class="card">
          <h2 class="text-base font-semibold text-gray-900 mb-3">Analysis Result</h2>
          <div class="flex items-center gap-3 mb-3">
            <div class="text-3xl font-bold text-blue-600">{{ analysisResult.score }}</div>
            <div class="text-sm text-gray-500">/ 100 score</div>
          </div>
          <ul class="space-y-1">
            @for (s of analysisResult.suggestions; track $index) {
              <li class="text-sm text-gray-600 flex gap-2">
                <span class="text-yellow-500 shrink-0">•</span>{{ s }}
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `
})
export class CvUploadComponent implements OnInit {
  private fb = inject(FormBuilder);
  private docApi = inject(DocumentApiService);
  private aiApi = inject(AiApiService);

  cvVersions: CvVersion[] = [];
  loading = false;
  success = false;
  error = '';
  analyzing: string | null = null;
  analysisResult: { score: number; suggestions: string[] } | null = null;

  form = this.fb.group({
    name: ['', Validators.required],
    content: ['', Validators.required]
  });

  ngOnInit(): void {
    this.docApi.getCvVersions().subscribe(cvs => this.cvVersions = cvs);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.success = false;
    const { name, content } = this.form.value;
    runAction({
      action$: this.docApi.uploadCv(name!, content!),
      setLoading: value => this.loading = value,
      setError: message => this.error = message,
      errorMessage: error => (error as any)?.error?.message || 'Upload failed',
      next: cv => {
        this.cvVersions.unshift(cv);
        this.form.reset();
        this.success = true;
      }
    });
  }

  analyze(cv: CvVersion): void {
    this.analyzing = cv.id;
    this.analysisResult = null;
    runAction({
      action$: this.aiApi.analyzeCv(cv.id),
      next: r => {
        this.analysisResult = { score: r.score, suggestions: r.suggestions };
        this.analyzing = null;
      },
      error: () => this.analyzing = null
    });
  }
}
