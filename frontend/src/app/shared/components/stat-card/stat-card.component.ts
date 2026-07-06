import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-stat-card',
  standalone: true,
  imports: [CommonModule, JbIconComponent],
  templateUrl: './stat-card.component.html'
})
export class StatCardComponent {
  @Input() label = '';
  @Input() value = '';
  @Input() sub = '';
  @Input() delta = '';
  @Input() icon = '';
  @Input() accent = false;
}
