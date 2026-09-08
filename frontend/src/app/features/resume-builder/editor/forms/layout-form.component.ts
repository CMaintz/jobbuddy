import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { TemplateType, SectionConfig, SectionTypography } from '../../models/resume-builder.models';
import { FONT_FAMILIES } from '../../data/font-families';

const TEMPLATES: { type: TemplateType; label: string; description: string }[] = [
  { type: 'classic', label: 'resumeBuilder.layout.tpl.classic.label', description: 'resumeBuilder.layout.tpl.classic.description' },
  { type: 'modern', label: 'resumeBuilder.layout.tpl.modern.label', description: 'resumeBuilder.layout.tpl.modern.description' },
  { type: 'modern-2col', label: 'resumeBuilder.layout.tpl.modern2col.label', description: 'resumeBuilder.layout.tpl.modern2col.description' },
  { type: 'minimal', label: 'resumeBuilder.layout.tpl.minimal.label', description: 'resumeBuilder.layout.tpl.minimal.description' },
  { type: 'executive', label: 'resumeBuilder.layout.tpl.executive.label', description: 'resumeBuilder.layout.tpl.executive.description' },
  { type: 'creative', label: 'resumeBuilder.layout.tpl.creative.label', description: 'resumeBuilder.layout.tpl.creative.description' },
];

/** Sections that can carry typography overrides (matches rbSection tags in the layouts). */
const TYPOGRAPHY_SECTIONS: { id: string; label: string }[] = [
  { id: 'header', label: 'resumeBuilder.layout.typoSection.header' },
  { id: 'summary', label: 'resumeBuilder.layout.typoSection.summary' },
  { id: 'experience', label: 'resumeBuilder.layout.typoSection.experience' },
  { id: 'education', label: 'resumeBuilder.layout.typoSection.education' },
  { id: 'projects', label: 'resumeBuilder.layout.typoSection.projects' },
  { id: 'skills', label: 'resumeBuilder.layout.typoSection.skills' },
  { id: 'languages', label: 'resumeBuilder.layout.typoSection.languages' },
  { id: 'certifications', label: 'resumeBuilder.layout.typoSection.certifications' },
  { id: 'strengths', label: 'resumeBuilder.layout.typoSection.strengths' },
  { id: 'socials', label: 'resumeBuilder.layout.typoSection.socials' },
  { id: 'custom', label: 'resumeBuilder.layout.typoSection.custom' },
];

@Component({
  selector: 'app-layout-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule, TranslateModule],
  templateUrl: './layout-form.component.html',
})
export class LayoutFormComponent {
  private state = inject(ResumeStateService);
  templates = TEMPLATES;
  typographySections = TYPOGRAPHY_SECTIONS;
  fonts = FONT_FAMILIES;
  expandedTypo = signal<string | null>(null);

  get settings() { return this.state.settings(); }
  get leftColumn() { return this.state.settings().leftColumn; }
  get rightColumn() { return this.state.settings().rightColumn; }

  selectTemplate(type: TemplateType): void {
    this.state.updateSettings({ template: type });
  }

  toggleSection(column: 'leftColumn' | 'rightColumn', index: number): void {
    const cols = [...this.state.settings()[column]];
    cols[index] = { ...cols[index], visible: !cols[index].visible };
    this.state.updateLayoutColumn(column, cols);
  }

  drop(column: 'leftColumn' | 'rightColumn', event: CdkDragDrop<SectionConfig[]>): void {
    const cols = [...this.state.settings()[column]];
    moveItemInArray(cols, event.previousIndex, event.currentIndex);
    this.state.updateLayoutColumn(column, cols);
  }

  // ── Per-section typography ────────────────────────────────────

  typo(id: string): SectionTypography {
    return this.state.settings().sectionTypography?.[id] ?? {};
  }

  hasOverrides(id: string): boolean {
    const t = this.typo(id);
    return !!(t.fontFamily || (t.sizeScale && t.sizeScale !== 1) || t.bold || t.italic || t.color);
  }

  setTypo(id: string, patch: Partial<SectionTypography>): void {
    const current = this.state.settings().sectionTypography ?? {};
    this.state.updateSettings({
      sectionTypography: { ...current, [id]: { ...current[id], ...patch } },
    });
  }

  resetTypo(id: string): void {
    const current = { ...(this.state.settings().sectionTypography ?? {}) };
    delete current[id];
    this.state.updateSettings({ sectionTypography: current });
  }
}
