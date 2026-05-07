import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AiApiService } from '../../../core/api/ai.api';
import { DocumentApiService } from '../../../core/api/document.api';
import { CvVersion } from '../../../core/models/cv-version.model';

@Component({
  selector: 'app-cv-analysis',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6 max-w-2xl mx-auto">
      <h1 class="text-3xl font-bold text-gray-900">CV Analysis</h1>

      @if (cvVersions.length === 0 && !loading) {
        <div class="card text-center py-12">
          <p class="text-gray-500">No CVs uploaded yet.</p>
          <a routerLink="/ai/cv" class="btn-primary mt-4 inline-block">Upload a CV</a>
        </div>
      } @else {
        <div class="card">
          <h2 class="text-base font-semibold text-gray-900 mb-3">Select CV to Analyze</h2>
          <div class="space-y-2">
            @for (cv of cvVersions; track cv.id) {
              <div class="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                <div>
                  <span class="font-medium">{{ cv.name }}</span>
                  <span class="text-xs text-gray-400 ml-2">v{{ cv.versionNumber }}</span>
                </div>
                <button (click)="analyze(cv)" [disabled]="analyzing === cv.id"
                        class="btn-primary text-sm">
                  {{ analyzing === cv.id ? 'Analyzing...' : 'Analyze' }}
                </button>
              </div>
            }
          </div>
        </div>

        @if (result) {
          <div class="card space-y-4">
            <div class="flex items-center gap-4">
              <div class="text-4xl font-bold text-blue-600">{{ result.score }}</div>
              <div>
                <div class="text-sm font-medium text-gray-700">CV Score</div>
                <div class="text-xs text-gray-400">out of 100</div>
              </div>
              <div class="flex-1 bg-gray-200 rounded-full h-3 ml-4">
                <div class="bg-blue-500 h-3 rounded-full transition-all"
                     [style.width.%]="result.score"></div>
              </div>
            </div>

            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Improvement Suggestions</h3>
              <ul class="space-y-2">
                @for (s of result.suggestions; track $index) {
                  <li class="flex gap-2 text-sm text-gray-600">
                    <span class="text-yellow-500 shrink-0 mt-0.5">→</span>
                    <span>{{ s }}</span>
                  </li>
                }
              </ul>
            </div>
          </div>
        }
      }
    </div>
  `
})
export class CvAnalysisComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private docApi = inject(DocumentApiService);

  cvVersions: CvVersion[] = [];
  loading = true;
  analyzing: string | null = null;
  result: { score: number; suggestions: string[] } | null = null;

  ngOnInit(): void {
    this.docApi.getCvVersions().subscribe({
      next: cvs => { this.cvVersions = cvs; this.loading = false; },
      error: () => this.loading = false
    });
  }

  analyze(cv: CvVersion): void {
    this.analyzing = cv.id;
    this.result = null;
    this.aiApi.analyzeCv(cv.id).subscribe({
      next: r => { this.result = { score: r.score, suggestions: r.suggestions }; this.analyzing = null; },
      error: () => this.analyzing = null
    });
  }
}
