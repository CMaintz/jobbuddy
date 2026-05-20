import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-form-actions',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex gap-2">
      <button type="submit" [disabled]="disabled" class="btn-primary text-sm px-3 py-1.5">
        {{ saveLabel }}
      </button>
      @if (showCancel) {
        <button type="button" (click)="cancel.emit()" class="btn-secondary text-sm px-3 py-1.5">
          {{ cancelLabel }}
        </button>
      }
    </div>
  `
})
export class FormActionsComponent {
  @Input() saveLabel = 'Save';
  @Input() cancelLabel = 'Cancel';
  @Input() showCancel = true;
  @Input() disabled = false;
  @Output() cancel = new EventEmitter<void>();
}
