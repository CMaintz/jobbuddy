import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { DebouncedTextareaComponent } from '../../shared/debounced-textarea.component';

@Component({
  selector: 'app-projects-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DebouncedTextareaComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (proj of projects; track proj.id) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <span class="text-xs font-medium text-gray-700">{{ proj.name || 'New Project' }}</span>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(proj.id)">Remove</button>
          </div>
          <label class="form-label">Project Name</label>
          <input class="form-input" [value]="proj.name" (input)="update(proj.id, 'name', $any($event.target).value)" placeholder="My Awesome Project" />
          <label class="form-label">Link / URL</label>
          <input class="form-input" [value]="proj.link" (input)="update(proj.id, 'link', $any($event.target).value)" placeholder="https://github.com/you/project" />
          <label class="form-label">Date</label>
          <input class="form-input" [value]="proj.date" (input)="update(proj.id, 'date', $any($event.target).value)" placeholder="2024-01" />
          <label class="form-label">Description</label>
          <app-debounced-textarea [value]="proj.description" [rows]="3" placeholder="What did you build and why?" (debouncedChange)="update(proj.id, 'description', $event)" />
          <label class="form-label">Skills (comma separated)</label>
          <input class="form-input" [value]="(proj.skills ?? []).join(', ')" (change)="updateSkills(proj.id, $any($event.target).value)" placeholder="React, Node.js, PostgreSQL" />
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Project</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class ProjectsFormComponent {
  private state = inject(ResumeStateService);
  get projects() { return this.state.projects(); }
  add(): void { this.state.addProject({ name: '', link: '', date: '', description: '', skills: [] }); }
  remove(id: string): void { this.state.removeProject(id); }
  update(id: string, field: string, value: any): void { this.state.updateProject(id, { [field]: value } as any); }
  updateSkills(id: string, value: string): void {
    const skills = value.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.state.updateProject(id, { skills });
  }
}
