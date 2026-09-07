import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeExperience } from '../../models/resume-builder.models';
import { DebouncedTextareaComponent } from '../../shared/debounced-textarea.component';
import { MonthYearPickerComponent } from '../../shared/month-year-picker.component';

@Component({
  selector: 'app-experience-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DebouncedTextareaComponent, MonthYearPickerComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (exp of experience; track exp.id; let i = $index) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <span class="text-xs font-medium text-gray-700">{{ exp.title || 'New Position' }}</span>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(exp.id)">Remove</button>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div class="col-span-2">
              <label class="form-label">Job Title</label>
              <input class="form-input" [value]="exp.title" (input)="update(exp.id, 'title', $any($event.target).value)" placeholder="Software Engineer" />
            </div>
            <div>
              <label class="form-label">Company</label>
              <input class="form-input" [value]="exp.company" (input)="update(exp.id, 'company', $any($event.target).value)" placeholder="Acme Corp" />
            </div>
            <div>
              <label class="form-label">Location</label>
              <input class="form-input" [value]="exp.location" (input)="update(exp.id, 'location', $any($event.target).value)" placeholder="Remote" />
            </div>
            <div>
              <label class="form-label">Start Date</label>
              <app-month-year-picker [value]="exp.startDate" (valueChange)="update(exp.id, 'startDate', $event)" />
            </div>
            <div>
              <label class="form-label">End Date</label>
              @if (!exp.current) {
                <app-month-year-picker [value]="exp.endDate" (valueChange)="update(exp.id, 'endDate', $event)" />
              } @else {
                <p class="text-xs text-gray-400 mt-2">Present</p>
              }
            </div>
            <div class="col-span-2 flex items-center gap-2">
              <input type="checkbox" [id]="'current-' + exp.id" [checked]="exp.current" (change)="update(exp.id, 'current', $any($event.target).checked)" class="rounded" />
              <label [for]="'current-' + exp.id" class="text-xs text-gray-600">Currently working here</label>
            </div>
            <div class="col-span-2">
              <label class="form-label">Description</label>
              <app-debounced-textarea [value]="exp.description" [rows]="3" placeholder="Describe your role and achievements..." (debouncedChange)="update(exp.id, 'description', $event)" />
            </div>
            <div class="col-span-2">
              <label class="form-label">Skills (comma separated)</label>
              <input class="form-input" [value]="(exp.skills ?? []).join(', ')" (change)="updateSkills(exp.id, $any($event.target).value)" placeholder="TypeScript, React, Node.js" />
            </div>
          </div>
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Work Experience</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class ExperienceFormComponent {
  private state = inject(ResumeStateService);
  get experience() { return this.state.experience(); }

  add(): void {
    this.state.addExperience({ title: '', company: '', location: '', startDate: '', endDate: '', current: false, description: '', skills: [] });
  }

  remove(id: string): void { this.state.removeExperience(id); }

  update(id: string, field: string, value: any): void {
    this.state.updateExperience(id, { [field]: value } as any);
  }

  updateSkills(id: string, value: string): void {
    const skills = value.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.state.updateExperience(id, { skills });
  }
}
