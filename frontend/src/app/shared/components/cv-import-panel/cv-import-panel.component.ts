import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Observable, of, switchMap } from 'rxjs';
import { JbIconComponent } from '../jb-icon/jb-icon.component';
import { JbButtonComponent } from '../jb-button/jb-button.component';
import { AiApiService } from '../../../core/api/ai.api';
import { Profile } from '../../../core/models/user.model';

/** Normalized preview of whatever the parser extracted, regardless of source. */
export interface ImportPreview {
  fullName?: string;
  headline?: string;
  summary?: string;
  skills: string[];
  technologies: string[];
  yearsExperience?: number;
}

/**
 * Three-mode CV import (PDF upload / paste text / LinkedIn PDF) with a parsed
 * preview and merge-apply into the master profile. Used by the Master CV
 * import page and onboarding step 3.
 */
@Component({
  selector: 'jb-cv-import-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent],
  templateUrl: './cv-import-panel.component.html',
})
export class CvImportPanelComponent {
  private http = inject(HttpClient);
  private aiApi = inject(AiApiService);

  /** Tighter spacing for embedded contexts like onboarding. */
  @Input() compact = false;
  /** Fires after the parsed data has been merged into the master profile. */
  @Output() applied = new EventEmitter<ImportPreview>();
  /** Surfaces errors to the host (toast placement differs per screen). */
  @Output() failed = new EventEmitter<string>();

  selectedMode = signal<'pdf' | 'paste' | 'linkedin'>('pdf');
  parsing = signal(false);
  applying = signal(false);
  preview = signal<ImportPreview | null>(null);

  pastedText = '';

  importOptions = [
    { key: 'pdf' as const, icon: 'upload', label: 'Upload PDF', description: 'Upload an existing CV and we\'ll parse it into structured sections.' },
    { key: 'paste' as const, icon: 'copy', label: 'Paste text', description: 'Paste your CV text and we\'ll extract the structure.' },
    { key: 'linkedin' as const, icon: 'link', label: 'LinkedIn', description: 'Upload your LinkedIn profile PDF export to auto-import.' },
  ];

  onCvPdfSelected(event: Event): void {
    const file = this.takeFile(event);
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    this.parse(this.http.post<Profile>('/api/v1/profile/import/cv-pdf', formData));
  }

  onLinkedInPdfSelected(event: Event): void {
    const file = this.takeFile(event);
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    this.parse(this.http.post<any>('/api/v1/users/me/import/linkedin-pdf', formData));
  }

  private takeFile(event: Event): File | null {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    return file;
  }

  parsePastedText(): void {
    if (this.pastedText.trim().length < 80) {
      this.failed.emit('Paste your full CV text first (at least a few lines)');
      return;
    }
    this.parse(this.aiApi.parseCv(this.pastedText));
  }

  private parse(request$: Observable<any>): void {
    if (this.parsing()) return;
    this.parsing.set(true);
    this.preview.set(null);
    request$.subscribe({
      next: (parsed: any) => {
        this.parsing.set(false);
        this.preview.set({
          fullName: parsed?.fullName || undefined,
          headline: parsed?.headline || undefined,
          summary: parsed?.summary || undefined,
          skills: parsed?.skills || [],
          technologies: parsed?.technologies || [],
          yearsExperience: parsed?.yearsExperience ?? undefined,
        });
      },
      error: () => {
        this.parsing.set(false);
        this.failed.emit('Parsing failed — check the file/text and try again');
      }
    });
  }

  /** Apply the parsed preview to the master profile. The backend merges: null fields keep their current value. */
  applyPreview(): void {
    const p = this.preview();
    if (!p || this.applying()) return;
    this.applying.set(true);

    this.http.patch('/api/v1/users/me/profile', {
      headline: p.headline ?? null,
      summary: p.summary ?? null,
      yearsExperience: p.yearsExperience ?? null,
      skills: p.skills.length ? p.skills : null,
      technologies: p.technologies.length ? p.technologies : null,
    }).pipe(
      switchMap(() => p.fullName
        ? this.http.patch('/api/v1/profile/private', { fullName: p.fullName })
        : of(null)),
    ).subscribe({
      next: () => {
        this.applying.set(false);
        this.applied.emit(p);
      },
      error: () => {
        this.applying.set(false);
        this.failed.emit('Could not apply the import — try again');
      }
    });
  }

  discardPreview(): void {
    this.preview.set(null);
  }
}
