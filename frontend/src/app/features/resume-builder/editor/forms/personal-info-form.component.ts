import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextEditorComponent } from '../../shared/rich-text-editor.component';

@Component({
  selector: 'app-personal-info-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RichTextEditorComponent],
  templateUrl: './personal-info-form.component.html',
  styleUrls: ['./personal-info-form.component.css'],
})
export class PersonalInfoFormComponent {
  private state = inject(ResumeStateService);
  get pi() { return this.state.personalInfo(); }
  update(field: string, value: string): void {
    this.state.updatePersonalInfo({ [field]: value } as any);
  }
}
