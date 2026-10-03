import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'jb-toggle',
  templateUrl: './jb-toggle.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./jb-toggle.component.css']
})
export class JbToggleComponent {
  @Input() on = false;
  @Output() toggled = new EventEmitter<boolean>();
}
