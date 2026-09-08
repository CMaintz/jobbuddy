import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-modal-shell',
  standalone: true,
  templateUrl: './modal-shell.component.html'
})
export class ModalShellComponent {
  @Input({ required: true }) title = '';
  @Output() closed = new EventEmitter<void>();
}
