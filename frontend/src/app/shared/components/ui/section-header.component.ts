import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-section-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex justify-between items-center mb-4">
      <h2 class="text-lg font-semibold">{{ title }}</h2>
      @if (actionLabel) {
        <button type="button" (click)="action.emit()" class="btn-primary text-sm px-3 py-1.5">
          {{ actionLabel }}
        </button>
      }
    </div>
  `
})
export class SectionHeaderComponent {
  @Input({ required: true }) title = '';
  @Input() actionLabel = '';
  @Output() action = new EventEmitter<void>();
}
