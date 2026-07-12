import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { ResumeStateService } from '../../services/resume-state.service';
import { IconPickerComponent } from '../../shared/icon-picker.component';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-strengths-form',
  standalone: true,
  imports: [CommonModule, FormsModule, LucideAngularModule, IconPickerComponent],
  templateUrl: './strengths-form.component.html',
})
export class StrengthsFormComponent {
  private state = inject(ResumeStateService);

  get strengths() { return this.state.strengths(); }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getIcon(key: string): any { return getStrengthIcon(key); }

  add(): void { this.state.addStrength({ title: '', description: '', iconKey: 'star' }); }
  remove(id: string): void { this.state.removeStrength(id); }
  update(id: string, field: string, value: string): void {
    this.state.updateStrength(id, { [field]: value } as never);
  }
}
