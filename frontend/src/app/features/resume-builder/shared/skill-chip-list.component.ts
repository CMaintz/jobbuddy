import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skill-chip-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (skills && skills.length > 0) {
      <div class="flex flex-wrap gap-1 mt-1">
        @for (skill of skills; track skill) {
          <span class="inline-block px-2 py-0.5 text-xs rounded bg-gray-100 text-gray-600 border border-gray-200">
            {{ skill }}
          </span>
        }
      </div>
    }
  `,
})
export class SkillChipListComponent {
  @Input() skills: string[] = [];
}
