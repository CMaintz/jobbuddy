import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface TabNavItem<T extends string = string> {
  key: T;
  label: string;
}

@Component({
  selector: 'app-tab-nav',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="border-b border-gray-200 mb-6">
      <nav class="-mb-px flex space-x-8">
        @for (tab of tabs; track tab.key) {
          <button type="button"
                  (click)="activeKeyChange.emit(tab.key)"
                  [class]="activeKey === tab.key
                    ? 'border-b-2 border-blue-600 text-blue-600 pb-2 font-medium text-sm'
                    : 'text-gray-500 hover:text-gray-700 pb-2 text-sm'">
            {{ tab.label }}
          </button>
        }
      </nav>
    </div>
  `
})
export class TabNavComponent<T extends string = string> {
  @Input() tabs: Array<TabNavItem<T>> = [];
  @Input() activeKey!: T;
  @Output() activeKeyChange = new EventEmitter<T>();
}
