import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AiApiService } from '../../../core/api/ai.api';
import { DocumentApiService } from '../../../core/api/document.api';
import { CvVersion } from '../../../core/models/cv-version.model';

interface ParsedProfile {
  fullName?: string;
  headline?: string;
  summary?: string;
  location?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  websiteUrl?: string;
  skills?: string[];
  technologies?: string[];
  languages?: string[];
}

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

          <!-- Apply to Profile Section -->
          <div class="card space-y-4">
            <h2 class="text-base font-semibold text-gray-900">Apply to Profile</h2>

            @if (!parsedProfile && !profileSaved) {
              @if (parseError) {
                <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ parseError }}</div>
              }
              <p class="text-sm text-gray-500">
                Parse your CV content to automatically extract and update your profile information.
              </p>
              <button (click)="parseCv()" [disabled]="parsing" class="btn-primary">
                {{ parsing ? 'Parsing...' : 'Parse CV → Update Profile' }}
              </button>
            }

            @if (parsedProfile) {
              <div class="bg-gray-50 rounded-lg p-4 space-y-3">
                <h3 class="text-sm font-semibold text-gray-700">Parsed Profile Preview</h3>

                @if (parsedProfile.fullName) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Name</span>
                    <p class="text-sm text-gray-800 font-medium">{{ parsedProfile.fullName }}</p>
                  </div>
                }
                @if (parsedProfile.headline) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Headline</span>
                    <p class="text-sm text-gray-800">{{ parsedProfile.headline }}</p>
                  </div>
                }
                @if (parsedProfile.summary) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Summary</span>
                    <p class="text-sm text-gray-800">{{ parsedProfile.summary }}</p>
                  </div>
                }
                @if (parsedProfile.location) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Location</span>
                    <p class="text-sm text-gray-800">{{ parsedProfile.location }}</p>
                  </div>
                }
                @if (parsedProfile.skills && parsedProfile.skills.length > 0) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Skills</span>
                    <div class="flex flex-wrap gap-1 mt-1">
                      @for (skill of parsedProfile.skills; track $index) {
                        <span class="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">{{ skill }}</span>
                      }
                    </div>
                  </div>
                }
                @if (parsedProfile.technologies && parsedProfile.technologies.length > 0) {
                  <div>
                    <span class="text-xs text-gray-400 uppercase tracking-wide">Technologies</span>
                    <div class="flex flex-wrap gap-1 mt-1">
                      @for (tech of parsedProfile.technologies; track $index) {
                        <span class="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">{{ tech }}</span>
                      }
                    </div>
                  </div>
                }
              </div>

              @if (saveError) {
                <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ saveError }}</div>
              }

              <div class="flex gap-3">
                <button (click)="saveToProfile()" [disabled]="savingProfile" class="btn-primary">
                  {{ savingProfile ? 'Saving...' : 'Save to Profile' }}
                </button>
                <button (click)="discardParsed()" class="btn-secondary">Discard</button>
              </div>
            }

            @if (profileSaved) {
              <div class="bg-green-50 text-green-700 rounded-md p-3 text-sm font-medium">
                Profile updated!
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class CvAnalysisComponent implements OnInit {
  private aiApi = inject(AiApiService);
  private docApi = inject(DocumentApiService);
  private http = inject(HttpClient);

  cvVersions: CvVersion[] = [];
  loading = true;
  analyzing: string | null = null;
  result: { score: number; suggestions: string[] } | null = null;

  selectedCv: CvVersion | null = null;
  parsing = false;
  parseError = '';
  parsedProfile: ParsedProfile | null = null;

  savingProfile = false;
  saveError = '';
  profileSaved = false;

  ngOnInit(): void {
    this.docApi.getCvVersions().subscribe({
      next: cvs => { this.cvVersions = cvs; this.loading = false; },
      error: () => this.loading = false
    });
  }

  analyze(cv: CvVersion): void {
    this.analyzing = cv.id;
    this.result = null;
    this.selectedCv = cv;
    this.parsedProfile = null;
    this.profileSaved = false;
    this.parseError = '';
    this.saveError = '';

    this.aiApi.analyzeCv(cv.id).subscribe({
      next: r => { this.result = { score: r.score, suggestions: r.suggestions }; this.analyzing = null; },
      error: () => this.analyzing = null
    });
  }

  parseCv(): void {
    if (!this.selectedCv?.content) {
      this.parseError = 'CV content is not available for parsing.';
      return;
    }
    this.parsing = true;
    this.parseError = '';

    this.http.post<ParsedProfile>('/api/v1/ai/parse-cv', { rawCvText: this.selectedCv.content }).subscribe({
      next: profile => {
        this.parsedProfile = profile;
        this.parsing = false;
      },
      error: () => {
        this.parseError = 'Failed to parse CV. Please try again.';
        this.parsing = false;
      }
    });
  }

  saveToProfile(): void {
    if (!this.parsedProfile) return;
    this.savingProfile = true;
    this.saveError = '';

    this.http.put('/api/v1/users/me/profile', this.parsedProfile).subscribe({
      next: () => {
        this.savingProfile = false;
        this.parsedProfile = null;
        this.profileSaved = true;
      },
      error: () => {
        this.saveError = 'Failed to save profile. Please try again.';
        this.savingProfile = false;
      }
    });
  }

  discardParsed(): void {
    this.parsedProfile = null;
    this.parseError = '';
    this.saveError = '';
    this.profileSaved = false;
  }
}
