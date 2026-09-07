import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-debounced-textarea',
  standalone: true,
  imports: [FormsModule],
  template: `
    <textarea
      class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
      [rows]="rows"
      [placeholder]="placeholder"
      [(ngModel)]="localValue"
      (ngModelChange)="onValueChange($event)"
    ></textarea>
  `,
})
export class DebouncedTextareaComponent implements OnChanges, OnDestroy {
  @Input() value = '';
  @Input() rows = 4;
  @Input() placeholder = '';
  @Input() debounceTime = 500;
  @Output() debouncedChange = new EventEmitter<string>();

  localValue = '';
  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] && changes['value'].currentValue !== this.localValue) {
      this.localValue = this.value;
    }
  }

  onValueChange(val: string): void {
    this.localValue = val;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.debouncedChange.emit(val), this.debounceTime);
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
  }
}
