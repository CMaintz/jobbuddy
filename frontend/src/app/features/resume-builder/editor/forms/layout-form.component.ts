import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { TemplateType, SectionConfig, SectionTypography } from '../../models/resume-builder.models';
import { FONT_FAMILIES } from '../../data/font-families';

const TEMPLATES: { type: TemplateType; label: string; description: string }[] = [
  { type: 'classic', label: 'Classic', description: 'Two-column with dark sidebar' },
  { type: 'modern', label: 'Modern (1-col)', description: 'Clean single-column layout' },
  { type: 'modern-2col', label: 'Modern (2-col)', description: 'Dark left column' },
  { type: 'minimal', label: 'Minimal', description: 'Minimalist with timeline' },
  { type: 'executive', label: 'Executive', description: 'Executive with header band' },
  { type: 'creative', label: 'Creative', description: 'Colorful accent style' },
];

/** Sections that can carry typography overrides (matches rbSection tags in the layouts). */
const TYPOGRAPHY_SECTIONS: { id: string; label: string }[] = [
  { id: 'header', label: 'Header / contact' },
  { id: 'summary', label: 'Summary' },
  { id: 'experience', label: 'Experience' },
  { id: 'education', label: 'Education' },
  { id: 'projects', label: 'Projects' },
  { id: 'skills', label: 'Skills' },
  { id: 'languages', label: 'Languages' },
  { id: 'certifications', label: 'Certifications' },
  { id: 'strengths', label: 'Strengths' },
  { id: 'socials', label: 'Socials' },
  { id: 'custom', label: 'Custom sections' },
];

@Component({
  selector: 'app-layout-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './layout-form.component.html',
  styleUrls: ['./layout-form.component.css'],
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
