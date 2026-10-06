import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideDynamicIcon, LucideIconInput } from '@lucide/angular';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { IconPickerComponent } from '../../shared/icon-picker.component';
import { DebouncedTextareaComponent } from '../../shared/debounced-textarea.component';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-strengths-form',
  imports: [
    FormsModule, LucideDynamicIcon, TranslateModule, IconPickerComponent,
    DebouncedTextareaComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './strengths-form.component.html',
})
export class StrengthsFormComponent {
  private state = inject(ResumeStateService);

  get strengths() { return this.state.strengths(); }

  getIcon(key: string): LucideIconInput { return getStrengthIcon(key); }

  add(): void { this.state.addStrength({ title: '', description: '', iconKey: 'star' }); }
  remove(id: string): void { this.state.removeStrength(id); }
  update(id: string, field: string, value: string): void {
    this.state.updateStrength(id, { [field]: value } as never);
  }
}
