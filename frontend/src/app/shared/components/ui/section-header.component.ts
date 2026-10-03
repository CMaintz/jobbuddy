import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-section-header',
  imports: [],
  templateUrl: './section-header.component.html'
})
export class SectionHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
