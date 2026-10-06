import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-modal-shell',
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './modal-shell.component.html'
})
export class ModalShellComponent {
  @Input({ required: true }) title = '';
  @Output() closed = new EventEmitter<void>();
}
