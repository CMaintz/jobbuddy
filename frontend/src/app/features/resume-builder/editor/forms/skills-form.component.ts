import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';

@Component({
  selector: 'app-skills-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col gap-3">
      @for (skill of skills; track skill.id) {
        <div class="flex items-center gap-2">
          <input class="form-input flex-1" [value]="skill.name" (input)="update(skill.id, 'name', $any($event.target).value)" placeholder="Skill name" />
          <select class="rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-20"
            [value]="skill.level ?? ''"
            (change)="update(skill.id, 'level', $any($event.target).value ? +$any($event.target).value : undefined)"
          >
            <option value="">Level</option>
            <option [value]="1">1</option>
            <option [value]="2">2</option>
            <option [value]="3">3</option>
            <option [value]="4">4</option>
            <option [value]="5">5</option>
          </select>
          <button class="text-red-400 hover:text-red-600 text-sm px-1" (click)="remove(skill.id)">✕</button>
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Skill</button>
      <p class="text-xs text-gray-400">Level 1–5 shown as dots in templates that support it. Toggle visibility in Settings.</p>
    </div>
  `,
  styles: [`
    .form-input { @apply rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class SkillsFormComponent {
  private state = inject(ResumeStateService);
  get skills() { return this.state.skills(); }
  add(): void { this.state.addSkill({ name: '', level: 3 }); }
  remove(id: string): void { this.state.removeSkill(id); }
  update(id: string, field: string, value: any): void { this.state.updateSkill(id, { [field]: value } as any); }
}
