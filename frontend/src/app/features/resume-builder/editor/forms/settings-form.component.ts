import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { FONT_FAMILIES } from '../../data/font-families';

@Component({
  selector: 'app-settings-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings-form.component.html',
})
export class SettingsFormComponent {
  private state = inject(ResumeStateService);
  fonts = FONT_FAMILIES;
  presetColors = ['#18324a', '#2563eb', '#16a34a', '#9333ea', '#dc2626', '#ea580c', '#0891b2', '#374151'];
  presetTextColors = ['#111827', '#1f2937', '#374151', '#0f172a', '#292524', '#1e3a5f'];
  get settings() { return this.state.settings(); }
  update(field: string, value: any): void { this.state.updateSettings({ [field]: value } as any); }
}
