import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-stat-card',
  imports: [JbIconComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
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
