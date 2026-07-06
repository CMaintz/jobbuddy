import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { STRENGTH_ICONS } from '../data/strength-icons';

@Component({
  selector: 'app-icon-picker',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  templateUrl: './icon-picker.component.html',
  styleUrls: ['./icon-picker.component.css'],
})
export class IconPickerComponent {
  @Input() selected = 'star';
  @Input() label = '';
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  @Input() icons: { key: string; label: string; icon: any }[] = STRENGTH_ICONS;
  @Output() iconSelected = new EventEmitter<string>();
}
