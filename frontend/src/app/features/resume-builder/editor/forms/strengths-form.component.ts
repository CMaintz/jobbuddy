import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { ResumeStateService } from '../../services/resume-state.service';
import { IconPickerComponent } from '../../shared/icon-picker.component';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-strengths-form',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, IconPickerComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (s of strengths; track s.id) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <lucide-icon [img]="getIcon(s.iconKey)" [size]="18" [strokeWidth]="1.5" class="text-gray-500"></lucide-icon>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(s.id)">Remove</button>
          </div>
          <label class="form-label">Title</label>
          <input class="form-input" [value]="s.title"
                 (input)="update(s.id, 'title', $any($event.target).value)"
                 placeholder="Problem Solving" />
          <label class="form-label">Description</label>
          <textarea class="form-input resize-none" rows="2" [value]="s.description"
                    (input)="update(s.id, 'description', $any($event.target).value)"
                    placeholder="Brief description of this strength..."></textarea>
          <app-icon-picker
            label="Icon"
            [selected]="s.iconKey"
            (iconSelected)="update(s.id, 'iconKey', $event)"
          />
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Strength</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class StrengthsFormComponent {
  private state = inject(ResumeStateService);

  get strengths() { return this.state.strengths(); }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getIcon(key: string): any { return getStrengthIcon(key); }

  add(): void { this.state.addStrength({ title: '', description: '', iconKey: 'star' }); }
  remove(id: string): void { this.state.removeStrength(id); }
  update(id: string, field: string, value: string): void {
    this.state.updateStrength(id, { [field]: value } as never);
  }
}
