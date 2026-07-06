import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AiApiService } from '../../../../core/api/ai.api';
import { DocumentApiService } from '../../../../core/api/document.api';
import { CvVersion } from '../../../../core/models/cv-version.model';

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
  templateUrl: './cv-analysis.component.html'
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
