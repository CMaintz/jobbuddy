import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeLanguage } from '../../models/resume-builder.models';
import { LANGUAGE_PROFICIENCIES } from '../../data/language-proficiencies';

@Component({
  selector: 'app-languages-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './languages-form.component.html',
})
export class LanguagesFormComponent {
  private state = inject(ResumeStateService);
  proficiencies = LANGUAGE_PROFICIENCIES;
  get languages() { return this.state.languages(); }
  add(): void { this.state.addLanguage({ name: '', proficiency: 'Fluent' }); }
  remove(id: string): void { this.state.removeLanguage(id); }
  update(id: string, field: string, value: unknown): void {
    this.state.updateLanguage(id, { [field]: value } as Partial<ResumeLanguage>);
  }
}
