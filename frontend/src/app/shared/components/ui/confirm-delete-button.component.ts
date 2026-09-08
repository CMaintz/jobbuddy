import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-confirm-delete-button',
  standalone: true,
  imports: [TranslateModule],
  templateUrl: './confirm-delete-button.component.html'
})
export class ConfirmDeleteButtonComponent {
  @Input() label = 'common.delete';
  @Input() confirmLabel = 'common.confirmDelete';
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
