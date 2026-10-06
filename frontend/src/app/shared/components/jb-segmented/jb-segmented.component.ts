import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'jb-segmented',
  imports: [],
  templateUrl: './jb-segmented.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./jb-segmented.component.css']
})
export class JbSegmentedComponent {
  @Input() options: string[] = [];
  @Input() value = '';
  @Output() changed = new EventEmitter<string>();
}
