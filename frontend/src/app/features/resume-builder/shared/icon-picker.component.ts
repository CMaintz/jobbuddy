import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { STRENGTH_ICONS } from '../data/strength-icons';

@Component({
  selector: 'app-icon-picker',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="flex flex-col gap-1.5">
      @if (label) {
        <span class="text-xs font-medium text-gray-600">{{ label }}</span>
      }
      <div class="grid grid-cols-8 gap-1">
        @for (icon of icons; track icon.key) {
          <button
            type="button"
            class="flex items-center justify-center h-8 w-8 rounded-md transition-all border"
            [title]="icon.label"
            [class.border-blue-500]="selected === icon.key"
            [class.bg-blue-50]="selected === icon.key"
            [class.text-blue-600]="selected === icon.key"
            [class.border-transparent]="selected !== icon.key"
            [class.text-gray-500]="selected !== icon.key"
            (click)="iconSelected.emit(icon.key)"
          >
            <lucide-icon [img]="icon.icon" [size]="16" [strokeWidth]="1.5"></lucide-icon>
          </button>
        }
      </div>
    </div>
  `,
})
export class IconPickerComponent {
  @Input() selected = 'star';
  @Input() label = '';
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  @Input() icons: { key: string; label: string; icon: any }[] = STRENGTH_ICONS;
  @Output() iconSelected = new EventEmitter<string>();
}
