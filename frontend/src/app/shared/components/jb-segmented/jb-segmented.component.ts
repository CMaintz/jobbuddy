import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'jb-segmented',
  imports: [],
  templateUrl: './jb-segmented.component.html',
  styleUrls: ['./jb-segmented.component.css']
})
export class JbSegmentedComponent {
  @Input() options: string[] = [];
  @Input() value = '';
  @Output() changed = new EventEmitter<string>();
}
