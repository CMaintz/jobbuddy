import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Jobbuddy icon set — stroked 16×16 viewBox SVG icons.
 * Usage: <jb-icon name="dashboard" [size]="14" />
 */
@Component({
  selector: 'jb-icon',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './jb-icon.component.html',
  styleUrls: ['./jb-icon.component.css'],
})
export class JbIconComponent {
  @Input() name = '';
  @Input() size = 14;
  @Input() strokeWidth = 1.5;
}
