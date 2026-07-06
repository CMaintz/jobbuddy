import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { AiApiService, GenerateDocumentRequest } from '../../core/api/ai.api';
import { JobsApiService } from '../../core/api/jobs.api';

@Component({
  selector: 'app-apply',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent],
  templateUrl: './apply.component.html',
  styleUrls: ['./apply.component.css']
})
export class ApplyComponent {
  private aiApi = inject(AiApiService);
  private router = inject(Router);

  phase = signal<'input' | 'generating'>('input');
  mode = signal<'paste' | 'url' | 'manual'>('paste');
  selectedFormat = signal('application');
  selectedVoice = signal('Warm');
  selectedLanguage = signal('English');
  activeStep = signal(0);

  company = '';
  role = '';
  location = '';
  jd = '';
  jobUrl = '';

  sourceOptions = [
    { mode: 'paste' as const, icon: 'copy', label: 'Paste JD text', sub: 'Paste from clipboard' },
    { mode: 'url' as const, icon: 'link', label: 'Paste URL', sub: 'We fetch + parse it' },
    { mode: 'manual' as const, icon: 'edit', label: 'Enter manually', sub: 'Type company, role & JD' },
  ];

  formats = [
    { key: 'application', label: 'Application' },
    { key: 'cover-letter', label: 'Cover letter' },
    { key: 'short-pitch', label: 'Short pitch' },
  ];

  voices = ['Direct', 'Warm', 'Formal'];
  languages = ['English', 'Dansk'];

  generatingSteps = [
    { label: 'Parse JD', note: '' },
    { label: 'Match master CV', note: '' },
    { label: 'Draft application', note: '' },
    { label: 'Polish & format', note: '' },
  ];

  get wordCount(): number {
    return this.jd.trim() ? this.jd.trim().split(/\s+/).length : 0;
  }

  isReady(): boolean {
    return !!(this.company.trim() && this.role.trim() &&
      (this.mode() === 'url' ? this.jobUrl.trim() : this.jd.trim().length > 40));
  }

  generate(): void {
    if (!this.isReady()) return;
    this.phase.set('generating');
    this.activeStep.set(0);

    // Animate through steps
    const advance = (step: number) => {
      if (step >= this.generatingSteps.length) return;
      setTimeout(() => {
        this.activeStep.set(step + 1);
        advance(step + 1);
      }, 700 + Math.random() * 500);
    };
    advance(0);

    // Actually call the API
    const req: GenerateDocumentRequest = {
      jobDescription: this.jd,
      documentType: this.selectedFormat() === 'cover-letter' ? 'COVER_LETTER' : 'APPLICATION',
      targetLanguage: this.selectedLanguage() === 'Dansk' ? 'da' : 'en',
      customInstructions: `Voice: ${this.selectedVoice()}. Company: ${this.company}. Role: ${this.role}. Location: ${this.location}`,
    };

    this.aiApi.generateDocument(req).subscribe({
      next: () => {
        // Navigate to pipeline after generation
        setTimeout(() => this.router.navigate(['/pipeline']), 500);
      },
      error: () => {
        // Stay on generating animation for demo, then go back
        setTimeout(() => this.phase.set('input'), 3000);
      }
    });
  }
}
