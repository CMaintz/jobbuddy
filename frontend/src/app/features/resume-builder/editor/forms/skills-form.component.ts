import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeSkill } from '../../models/resume-builder.models';

@Component({
  selector: 'app-skills-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './skills-form.component.html',
})
export class SkillsFormComponent {
  private state = inject(ResumeStateService);
  get skills() { return this.state.skills(); }
  add(): void { this.state.addSkill({ name: '', level: 3 }); }
  remove(id: string): void { this.state.removeSkill(id); }
  update(id: string, field: string, value: unknown): void { this.state.updateSkill(id, { [field]: value } as Partial<ResumeSkill>); }
}
