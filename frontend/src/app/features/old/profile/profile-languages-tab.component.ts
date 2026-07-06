import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EmptyStateComponent } from '../../../shared/components/ui/empty-state.component';
import { FormActionsComponent } from '../../../shared/components/ui/form-actions.component';
import { ConfirmDeleteButtonComponent } from '../../../shared/components/ui/confirm-delete-button.component';
import { InlineFormPanelComponent } from '../../../shared/components/ui/inline-form-panel.component';
import { SectionHeaderComponent } from '../../../shared/components/ui/section-header.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-languages-tab',
  standalone: true,
  imports: [CommonModule, FormsModule, EmptyStateComponent, FormActionsComponent, ConfirmDeleteButtonComponent, InlineFormPanelComponent, SectionHeaderComponent],
  templateUrl: './profile-languages-tab.component.html'
})
export class SpokenLanguagesTabComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
