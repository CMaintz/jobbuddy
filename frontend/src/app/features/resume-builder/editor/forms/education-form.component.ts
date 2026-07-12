import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { MonthYearPickerComponent } from '../../shared/month-year-picker.component';

@Component({
  selector: 'app-education-form',
  standalone: true,
  imports: [CommonModule, FormsModule, MonthYearPickerComponent],
  templateUrl: './education-form.component.html',
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
