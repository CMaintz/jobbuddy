import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeProject } from '../../models/resume-builder.models';
import { RichTextEditorComponent } from '../../shared/rich-text-editor.component';
import { AiRefineMenuComponent } from '../../shared/ai-refine-menu.component';

@Component({
  selector: 'app-projects-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RichTextEditorComponent, AiRefineMenuComponent],
  templateUrl: './projects-form.component.html',
})
export class ProjectsFormComponent {
  private state = inject(ResumeStateService);
  get projects() { return this.state.projects(); }
  get jobDescription() { return this.state.jobDescription() ?? undefined; }
  add(): void { this.state.addProject({ name: '', link: '', date: '', description: '', skills: [] }); }
  remove(id: string): void { this.state.removeProject(id); }
  update(id: string, field: string, value: unknown): void { this.state.updateProject(id, { [field]: value } as Partial<ResumeProject>); }
  updateSkills(id: string, value: string): void {
    const skills = value.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.state.updateProject(id, { skills });
  }
}
