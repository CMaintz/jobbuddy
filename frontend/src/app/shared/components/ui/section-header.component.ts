import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-section-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './section-header.component.html'
})
export class SectionHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
