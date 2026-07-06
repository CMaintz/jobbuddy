import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalShellComponent } from '../../../shared/components/ui/modal-shell.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-new-skill-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalShellComponent],
  templateUrl: './profile-new-skill-modal.component.html'
})
export class ProfileNewSkillModalComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
