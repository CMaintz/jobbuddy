import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmDeleteButtonComponent } from '../../../shared/components/ui/confirm-delete-button.component';
import { EmptyStateComponent } from '../../../shared/components/ui/empty-state.component';
import { FormActionsComponent } from '../../../shared/components/ui/form-actions.component';
import { InlineFormPanelComponent } from '../../../shared/components/ui/inline-form-panel.component';
import { SectionHeaderComponent } from '../../../shared/components/ui/section-header.component';
import type { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-profile-strengths-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ConfirmDeleteButtonComponent,
    EmptyStateComponent,
    FormActionsComponent,
    InlineFormPanelComponent,
    SectionHeaderComponent,
  ],
  templateUrl: './profile-strengths-tab.component.html',
})
export class ProfileStrengthsTabComponent {
  @Input({ required: true }) vm!: ProfileComponent;
}
