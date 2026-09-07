import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-modal-shell',
  standalone: true,
  template: `
    <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-lg shadow-xl max-w-2xl w-full max-h-[80vh] overflow-y-auto p-6">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900">{{ title }}</h2>
          <button type="button" (click)="closed.emit()" class="text-gray-400 hover:text-gray-600 text-xl">x</button>
        </div>
        <ng-content></ng-content>
      </div>
    </div>
  `
})
export class ModalShellComponent {
  @Input({ required: true }) title = '';
  @Output() closed = new EventEmitter<void>();
}
