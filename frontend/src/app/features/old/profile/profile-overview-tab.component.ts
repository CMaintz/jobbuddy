import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { FileUploadButtonComponent } from '../../../shared/components/file-upload-button/file-upload-button.component';
import { FormActionsComponent } from '../../../shared/components/ui/form-actions.component';
import { ModalShellComponent } from '../../../shared/components/ui/modal-shell.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-overview-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FileUploadButtonComponent, FormActionsComponent, ModalShellComponent],
  templateUrl: './profile-overview-tab.component.html'
})
export class ProfileOverviewTabComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
