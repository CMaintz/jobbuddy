import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalShellComponent } from '../../../shared/components/ui/modal-shell.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-new-skill-modal',
  imports: [FormsModule, ModalShellComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './profile-new-skill-modal.component.html'
})
export class ProfileNewSkillModalComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
