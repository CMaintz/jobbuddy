import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';

export interface TabNavItem<T extends string = string> {
  key: T;
  label: string;
}

@Component({
  selector: 'app-tab-nav',
  imports: [],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './tab-nav.component.html'
})
export class TabNavComponent<T extends string = string> {
  @Input() tabs: TabNavItem<T>[] = [];
  @Input() activeKey!: T;
  @Output() activeKeyChange = new EventEmitter<T>();
}
