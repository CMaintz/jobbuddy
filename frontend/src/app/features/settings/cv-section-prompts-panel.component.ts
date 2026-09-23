import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { CvSectionPromptsApiService } from '../../core/api/cv-section-prompts.api';

interface SectionField { key: string; label: string; }

/**
 * Standing per-section CV tailoring prompts ("how I want my profile / competencies / … written").
 * Saved once per user and applied whenever a CV is tailored, reviewed, or a field is refined.
 */
@Component({
  selector: 'app-cv-section-prompts-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  template: `
    <div class="py-3 border-b border-jb-border-faint">
      <div class="text-base font-medium">{{ 'settings.sectionPrompts.heading' | translate }}</div>
      <div class="text-xs text-jb-text-dim mt-0.5 mb-3 leading-[1.55]">{{ 'settings.sectionPrompts.intro' | translate }}</div>
      @if (loadError()) {
        <div class="text-xs text-jb-danger">{{ 'settings.sectionPrompts.loadFailed' | translate }}</div>
      } @else if (!loading()) {
        <div class="flex flex-col gap-3">
          @for (f of fields; track f.key) {
            <label class="block">
              <span class="block text-sm font-medium mb-1">{{ f.label | translate }}</span>
              <textarea [ngModel]="prompts[f.key] || ''" (ngModelChange)="onInput(f.key, $event)"
                [placeholder]="'settings.sectionPrompts.placeholder' | translate"
                class="w-full min-h-[64px] p-2.5 bg-jb-surface-2 border border-jb-border rounded-md text-jb-text text-sm leading-relaxed resize-y"></textarea>
            </label>
          }
          <div class="flex items-center gap-3">
            <button type="button" (click)="save()" [disabled]="!dirty() || saving()"
              class="px-3 py-[6px] bg-jb-surface-2 border border-jb-accent-border rounded-md text-jb-text text-sm cursor-pointer disabled:opacity-50">
              {{ (saving() ? 'settings.sectionPrompts.saving' : 'settings.sectionPrompts.save') | translate }}
            </button>
            @if (saved()) {
              <span class="text-2xs text-jb-text-dim">{{ 'settings.sectionPrompts.saved' | translate }}</span>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class CvSectionPromptsPanelComponent implements OnInit {
  private api = inject(CvSectionPromptsApiService);

  loading = signal(true);
  /** A failed load must NOT render an empty editable form — saving then would wipe saved prompts. */
  loadError = signal(false);
  saving = signal(false);
  saved = signal(false);
  dirty = signal(false);
  prompts: Record<string, string> = {};

  readonly fields: SectionField[] = [
    { key: 'profile', label: 'settings.sectionPrompts.field.profile' },
    { key: 'skills', label: 'settings.sectionPrompts.field.competencies' },
    { key: 'experience', label: 'settings.sectionPrompts.field.experience' },
    { key: 'projects', label: 'settings.sectionPrompts.field.projects' },
    { key: 'education', label: 'settings.sectionPrompts.field.education' },
    { key: 'certifications', label: 'settings.sectionPrompts.field.certifications' },
  ];

  ngOnInit(): void {
    this.api.get().subscribe({
      next: res => { this.prompts = res.prompts ?? {}; this.loading.set(false); },
      error: () => { this.loadError.set(true); this.loading.set(false); },
    });
  }

  onInput(key: string, value: string): void {
    this.prompts = { ...this.prompts, [key]: value };
    this.dirty.set(true);
    this.saved.set(false);
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.save(this.prompts).subscribe({
      next: res => {
        this.prompts = res.prompts ?? {};
        this.saving.set(false);
        this.dirty.set(false);
        this.saved.set(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
