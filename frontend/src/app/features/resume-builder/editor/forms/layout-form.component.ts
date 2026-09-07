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
  template: `
    <div class="flex flex-col gap-5">
      <!-- Template picker -->
      <div>
        <label class="form-label">Template</label>
        <div class="grid grid-cols-2 gap-2">
          @for (tpl of templates; track tpl.type) {
            <button
              class="p-3 text-left rounded-lg border-2 transition-colors"
              [class.border-blue-600]="settings.template === tpl.type"
              [class.bg-blue-50]="settings.template === tpl.type"
              [class.border-gray-200]="settings.template !== tpl.type"
              (click)="selectTemplate(tpl.type)"
            >
              <p class="text-xs font-semibold text-gray-800">{{ tpl.label }}</p>
              <p class="text-xs text-gray-400 mt-0.5">{{ tpl.description }}</p>
            </button>
          }
        </div>
      </div>

      <!-- Left column sections -->
      <div>
        <label class="form-label">Left Column Sections</label>
        <div class="flex flex-col gap-1">
          @for (section of leftColumn; track section.id; let i = $index) {
            <div class="flex items-center gap-2 p-2 bg-gray-50 rounded border border-gray-200">
              <span class="text-gray-400 cursor-move text-xs">⠿</span>
              <span class="flex-1 text-xs text-gray-700">{{ section.name }}</span>
              <button
                class="text-xs px-2 py-0.5 rounded"
                [class.text-green-700]="section.visible"
                [class.text-gray-400]="!section.visible"
                (click)="toggleSection('leftColumn', i)"
              >{{ section.visible ? 'Visible' : 'Hidden' }}</button>
            </div>
          }
        </div>
      </div>

      <!-- Right column sections -->
      <div>
        <label class="form-label">Right Column Sections</label>
        <div class="flex flex-col gap-1">
          @for (section of rightColumn; track section.id; let i = $index) {
            <div class="flex items-center gap-2 p-2 bg-gray-50 rounded border border-gray-200">
              <span class="text-gray-400 cursor-move text-xs">⠿</span>
              <span class="flex-1 text-xs text-gray-700">{{ section.name }}</span>
              <button
                class="text-xs px-2 py-0.5 rounded"
                [class.text-green-700]="section.visible"
                [class.text-gray-400]="!section.visible"
                (click)="toggleSection('rightColumn', i)"
              >{{ section.visible ? 'Visible' : 'Hidden' }}</button>
            </div>
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
  `],
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
