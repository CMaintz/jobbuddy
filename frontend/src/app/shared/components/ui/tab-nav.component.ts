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
  templateUrl: './tab-nav.component.html'
})
export class TabNavComponent<T extends string = string> {
  @Input() tabs: Array<TabNavItem<T>> = [];
  @Input() activeKey!: T;
  @Output() activeKeyChange = new EventEmitter<T>();
}
