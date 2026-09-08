import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jb-segmented',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './jb-segmented.component.html',
  styleUrls: ['./jb-segmented.component.css']
})
export class JbSegmentedComponent {
  @Input() options: string[] = [];
  @Input() value = '';
  @Output() changed = new EventEmitter<string>();
}
