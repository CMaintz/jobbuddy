import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { LANGUAGE_PROFICIENCIES } from '../../data/language-proficiencies';

@Component({
  selector: 'app-languages-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col gap-3">
      @for (lang of languages; track lang.id) {
        <div class="flex items-center gap-2">
          <input class="form-input flex-1" [value]="lang.name" (input)="update(lang.id, 'name', $any($event.target).value)" placeholder="English" />
          <select class="rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            [value]="lang.proficiency" (change)="update(lang.id, 'proficiency', $any($event.target).value)">
            @for (p of proficiencies; track p) {
              <option [value]="p">{{ p }}</option>
            }
          </select>
          <button class="text-red-400 hover:text-red-600 text-sm px-1" (click)="remove(lang.id)">✕</button>
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Language</button>
    </div>
  `,
  styles: [`
    .form-input { @apply rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class LanguagesFormComponent {
  private state = inject(ResumeStateService);
  proficiencies = LANGUAGE_PROFICIENCIES;
  get languages() { return this.state.languages(); }
  add(): void { this.state.addLanguage({ name: '', proficiency: 'Fluent' }); }
  remove(id: string): void { this.state.removeLanguage(id); }
  update(id: string, field: string, value: any): void { this.state.updateLanguage(id, { [field]: value } as any); }
}
