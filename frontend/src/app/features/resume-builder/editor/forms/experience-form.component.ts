import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeExperience } from '../../models/resume-builder.models';
import { RichTextEditorComponent } from '../../shared/rich-text-editor.component';
import { AiRefineMenuComponent } from '../../shared/ai-refine-menu.component';
import { MonthYearPickerComponent } from '../../shared/month-year-picker.component';

@Component({
  selector: 'app-experience-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RichTextEditorComponent, MonthYearPickerComponent, AiRefineMenuComponent],
  templateUrl: './experience-form.component.html',
})
export class ExperienceFormComponent {
  private state = inject(ResumeStateService);
  get experience() { return this.state.experience(); }
  get jobDescription() { return this.state.jobDescription() ?? undefined; }

  add(): void {
    this.state.addExperience({ title: '', company: '', location: '', startDate: '', endDate: '', current: false, description: '', skills: [] });
  }

  remove(id: string): void { this.state.removeExperience(id); }

  update(id: string, field: string, value: unknown): void {
    this.state.updateExperience(id, { [field]: value } as Partial<ResumeExperience>);
  }

  updateSkills(id: string, value: string): void {
    const skills = value.split(',').map((s: string) => s.trim()).filter((s: string) => s.length > 0);
    this.state.updateExperience(id, { skills });
  }
}
