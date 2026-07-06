import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-form-actions',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './form-actions.component.html'
})
export class FormActionsComponent {
  @Input() saveLabel = 'Save';
  @Input() cancelLabel = 'Cancel';
  @Input() showCancel = true;
  @Input() disabled = false;
  @Output() cancel = new EventEmitter<void>();
}
