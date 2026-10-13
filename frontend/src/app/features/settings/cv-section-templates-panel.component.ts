import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { PromptApiService } from '../../core/api/prompt.api';
import { CV_SECTION_KEYS, CvSectionKey, templatesForSection } from '../../core/models/cv-section-category';
import { PromptTemplate } from '../../core/models/prompt-template.model';
import { PromptExplainerComponent } from '../../shared/components/prompt-explainer/prompt-explainer.component';

type SaveStatus = 'saving' | 'saved' | 'failed';

interface SectionRow { key: CvSectionKey; label: string; templates: PromptTemplate[] }

const SECTION_LABELS: Record<CvSectionKey, string> = {
  profile: 'settings.sectionPrompts.field.profile',
  skills: 'settings.sectionPrompts.field.competencies',
  experience: 'settings.sectionPrompts.field.experience',
  projects: 'settings.sectionPrompts.field.projects',
  education: 'settings.sectionPrompts.field.education',
  certifications: 'settings.sectionPrompts.field.certifications',
};

/** The template a section uses now; the app default stands in if the server marks none. */
export function inUseTemplate(templates: PromptTemplate[]): PromptTemplate | undefined {
  return templates.find(t => t.isSelectedDefault) ?? templates.find(t => t.isDefault);
}

/**
 * Picking the app's own template clears the user's choice rather than recording the app default
 * as a choice, so a later change to the shipped default still reaches this user.
 */
export function makeDefault(api: PromptApiService, template: PromptTemplate): Observable<void> {
  return template.isDefault && template.category
    ? api.resetDefault(template.category)
    : api.selectDefault(template.id);
}

/** Which writing-style template each CV section is tailored and refined with. */
@Component({
  selector: 'app-cv-section-templates-panel',
  imports: [FormsModule, RouterLink, TranslateModule, PromptExplainerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './cv-section-templates-panel.component.html',
})
export class CvSectionTemplatesPanelComponent implements OnInit {
  private promptApi = inject(PromptApiService);

  loading = signal(true);
  loadError = signal(false);
  rows = signal<SectionRow[]>([]);
  status = signal<Partial<Record<CvSectionKey, SaveStatus>>>({});
  /** Template id shown in each section's select. */
  selected: Partial<Record<CvSectionKey, string>> = {};

  ngOnInit(): void {
    this.promptApi.getAll().subscribe({
      next: all => {
        const rows = CV_SECTION_KEYS.map(key => ({
          key, label: SECTION_LABELS[key], templates: templatesForSection(all, key),
        }));
        rows.forEach(row => (this.selected[row.key] = inUseTemplate(row.templates)?.id));
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => { this.loadError.set(true); this.loading.set(false); },
    });
  }

  selectedTemplate(row: SectionRow): PromptTemplate | undefined {
    return row.templates.find(t => t.id === this.selected[row.key]);
  }

  choose(row: SectionRow, id: string): void {
    const template = row.templates.find(t => t.id === id);
    const previous = inUseTemplate(row.templates);
    if (!template || template.id === previous?.id) return;
    this.selected[row.key] = id;
    this.setStatus(row.key, 'saving');
    makeDefault(this.promptApi, template).subscribe({
      next: () => {
        this.markInUse(row, template.id);
        this.setStatus(row.key, 'saved');
      },
      error: () => {
        this.selected[row.key] = previous?.id;
        this.setStatus(row.key, 'failed');
      },
    });
  }

  private markInUse(row: SectionRow, id: string): void {
    row.templates = row.templates.map(t => ({ ...t, isSelectedDefault: t.id === id }));
  }

  private setStatus(key: CvSectionKey, status: SaveStatus): void {
    this.status.set({ ...this.status(), [key]: status });
  }
}
