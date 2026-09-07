import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { EmptyStateComponent } from '../../shared/components/ui/empty-state.component';
import { FormActionsComponent } from '../../shared/components/ui/form-actions.component';
import { ConfirmDeleteButtonComponent } from '../../shared/components/ui/confirm-delete-button.component';
import { InlineFormPanelComponent } from '../../shared/components/ui/inline-form-panel.component';
import { SectionHeaderComponent } from '../../shared/components/ui/section-header.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-certifications-tab',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, EmptyStateComponent, FormActionsComponent, ConfirmDeleteButtonComponent, InlineFormPanelComponent, SectionHeaderComponent],
  templateUrl: './profile-certifications-tab.component.html'
})
export class ProfileCertificationsTabComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
