import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { TemplateType, SectionConfig } from '../../models/resume-builder.models';

const TEMPLATES: { type: TemplateType; label: string; description: string }[] = [
  { type: 'classic', label: 'Classic', description: 'Two-column with dark sidebar' },
  { type: 'modern', label: 'Modern (1-col)', description: 'Clean single-column layout' },
  { type: 'modern-2col', label: 'Modern (2-col)', description: 'Dark left column' },
  { type: 'minimal', label: 'Minimal', description: 'Minimalist with timeline' },
  { type: 'executive', label: 'Executive', description: 'Executive with header band' },
  { type: 'creative', label: 'Creative', description: 'Colorful accent style' },
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
}
