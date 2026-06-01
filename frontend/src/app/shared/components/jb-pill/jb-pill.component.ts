import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-pill',
  standalone: true,
  template: `<span [class]="'pill pill-' + tone"><ng-content /></span>`,
  styles: [`:host { @apply inline-flex; }`]
})
export class JbPillComponent {
  @Input() tone: 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet' = 'neutral';
}
