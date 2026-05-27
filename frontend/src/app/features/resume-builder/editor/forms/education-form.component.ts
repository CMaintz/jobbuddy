import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { MonthYearPickerComponent } from '../../shared/month-year-picker.component';

@Component({
  selector: 'app-education-form',
  standalone: true,
  imports: [CommonModule, FormsModule, MonthYearPickerComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (edu of education; track edu.id) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <span class="text-xs font-medium text-gray-700">{{ edu.degree || 'New Education' }}</span>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(edu.id)">Remove</button>
          </div>
          <label class="form-label">Degree</label>
          <input class="form-input" [value]="edu.degree" (input)="update(edu.id, 'degree', $any($event.target).value)" placeholder="Bachelor of Science" />
          <label class="form-label">School</label>
          <input class="form-input" [value]="edu.school" (input)="update(edu.id, 'school', $any($event.target).value)" placeholder="MIT" />
          <label class="form-label">Location</label>
          <input class="form-input" [value]="edu.location" (input)="update(edu.id, 'location', $any($event.target).value)" placeholder="Cambridge, MA" />
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="form-label">Start Date</label>
              <app-month-year-picker [value]="edu.startDate" (valueChange)="update(edu.id, 'startDate', $event)" />
            </div>
            <div>
              <label class="form-label">End Date</label>
              <app-month-year-picker [value]="edu.endDate" (valueChange)="update(edu.id, 'endDate', $event)" />
            </div>
          </div>
          <div class="flex items-center gap-2">
            <input type="checkbox" [id]="'current-edu-' + edu.id" [checked]="edu.current" (change)="update(edu.id, 'current', $any($event.target).checked)" class="rounded" />
            <label [for]="'current-edu-' + edu.id" class="text-xs text-gray-600">Currently studying</label>
          </div>
          <label class="form-label">Skills (comma separated)</label>
          <input class="form-input" [value]="(edu.skills ?? []).join(', ')" (change)="updateSkills(edu.id, $any($event.target).value)" placeholder="Java, Python, Data Structures" />
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Education</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class EducationFormComponent {
  private state = inject(ResumeStateService);
  get education() { return this.state.education(); }
  add(): void { this.state.addEducation({ degree: '', school: '', location: '', startDate: '', endDate: '', current: false, skills: [] }); }
  remove(id: string): void { this.state.removeEducation(id); }
  update(id: string, field: string, value: any): void { this.state.updateEducation(id, { [field]: value } as any); }
  updateSkills(id: string, value: string): void {
    const skills = value.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.state.updateEducation(id, { skills });
  }
}
