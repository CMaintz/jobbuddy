import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  template: `<p class="text-gray-500 text-sm">{{ message }}</p>`
})
export class EmptyStateComponent {
  @Input({ required: true }) message = '';
}
