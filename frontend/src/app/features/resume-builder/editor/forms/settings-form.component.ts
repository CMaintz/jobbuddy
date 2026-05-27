import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { FONT_FAMILIES } from '../../data/font-families';

@Component({
  selector: 'app-settings-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col gap-4">
      <div>
        <label class="form-label">Theme Color</label>
        <div class="flex items-center gap-2">
          <input type="color" class="h-9 w-14 rounded-md border border-gray-300 cursor-pointer"
            [value]="settings.themeColor"
            (input)="update('themeColor', $any($event.target).value)" />
          <div class="flex flex-wrap gap-1.5">
            @for (color of presetColors; track color) {
              <button
                class="w-6 h-6 rounded-full border-2 transition-transform hover:scale-110"
                [style.background-color]="color"
                [style.border-color]="settings.themeColor === color ? color : 'transparent'"
                (click)="update('themeColor', color)"
              ></button>
            }
          </div>
        </div>
      </div>

      <div>
        <label class="form-label">Font Family</label>
        <select class="form-input" [value]="settings.fontFamily" (change)="update('fontFamily', $any($event.target).value)">
          @for (font of fonts; track font.value) {
            <option [value]="font.value">{{ font.label }}</option>
          }
        </select>
      </div>

      <div>
        <label class="form-label">Font Size</label>
        <select class="form-input" [value]="settings.fontSize" (change)="update('fontSize', $any($event.target).value)">
          <option value="sm">Small</option>
          <option value="md">Medium</option>
          <option value="lg">Large</option>
          <option value="xl">Extra Large</option>
        </select>
      </div>

      <div>
        <label class="form-label">Document Size</label>
        <div class="flex gap-2">
          <button class="flex-1 py-2 text-sm rounded-lg border transition-colors"
            [class.bg-blue-600]="settings.documentSize === 'A4'"
            [class.text-white]="settings.documentSize === 'A4'"
            [class.border-blue-600]="settings.documentSize === 'A4'"
            [class.border-gray-300]="settings.documentSize !== 'A4'"
            (click)="update('documentSize', 'A4')">A4</button>
          <button class="flex-1 py-2 text-sm rounded-lg border transition-colors"
            [class.bg-blue-600]="settings.documentSize === 'Letter'"
            [class.text-white]="settings.documentSize === 'Letter'"
            [class.border-blue-600]="settings.documentSize === 'Letter'"
            [class.border-gray-300]="settings.documentSize !== 'Letter'"
            (click)="update('documentSize', 'Letter')">Letter</button>
        </div>
      </div>

      <div>
        <label class="form-label">Photo Style</label>
        <div class="flex gap-2">
          @for (style of ['square', 'rounded', 'circle']; track style) {
            <button class="flex-1 py-2 text-xs rounded-lg border capitalize transition-colors"
              [class.bg-blue-600]="settings.photoStyle === style"
              [class.text-white]="settings.photoStyle === style"
              [class.border-blue-600]="settings.photoStyle === style"
              [class.border-gray-300]="settings.photoStyle !== style"
              (click)="update('photoStyle', style)">{{ style }}</button>
          }
        </div>
      </div>

      <div class="flex items-center justify-between">
        <label class="form-label !mb-0">Show Skill Level (dots)</label>
        <button
          class="relative inline-flex h-5 w-9 rounded-full transition-colors"
          [class.bg-blue-600]="settings.showSkillLevel"
          [class.bg-gray-300]="!settings.showSkillLevel"
          (click)="update('showSkillLevel', !settings.showSkillLevel)"
        >
          <span class="absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform"
            [class.translate-x-4]="settings.showSkillLevel"
            [class.translate-x-0.5]="!settings.showSkillLevel"
          ></span>
        </button>
      </div>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
  `],
})
export class SettingsFormComponent {
  private state = inject(ResumeStateService);
  fonts = FONT_FAMILIES;
  presetColors = ['#18324a', '#2563eb', '#16a34a', '#9333ea', '#dc2626', '#ea580c', '#0891b2', '#374151'];
  get settings() { return this.state.settings(); }
  update(field: string, value: any): void { this.state.updateSettings({ [field]: value } as any); }
}
