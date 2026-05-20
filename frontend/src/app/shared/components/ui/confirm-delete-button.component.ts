import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-confirm-delete-button',
  standalone: true,
  template: `
    <button type="button" (click)="onClick()" [class]="buttonClass">
      {{ confirming ? confirmLabel : label }}
    </button>
  `
})
export class ConfirmDeleteButtonComponent {
  @Input() label = 'Delete';
  @Input() confirmLabel = 'Confirm delete';
  @Input() buttonClass = 'btn-danger text-sm';
  @Output() confirmed = new EventEmitter<void>();

  confirming = false;

  onClick(): void {
    if (!this.confirming) {
      this.confirming = true;
      setTimeout(() => this.confirming = false, 2500);
      return;
    }
    this.confirming = false;
    this.confirmed.emit();
  }
}
