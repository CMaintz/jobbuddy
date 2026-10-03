import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-section-header',
  imports: [],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './section-header.component.html'
})
export class SectionHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
