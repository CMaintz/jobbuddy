import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { LucideDynamicIcon, LucideIconInput } from '@lucide/angular';
import { STRENGTH_ICONS } from '../data/strength-icons';

@Component({
  selector: 'app-icon-picker',
  imports: [LucideDynamicIcon],
  templateUrl: './icon-picker.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./icon-picker.component.css'],
})
export class IconPickerComponent {
  @Input() selected = 'star';
  @Input() label = '';
  @Input() icons: { key: string; label: string; icon: LucideIconInput }[] = STRENGTH_ICONS;
  @Output() iconSelected = new EventEmitter<string>();
}
