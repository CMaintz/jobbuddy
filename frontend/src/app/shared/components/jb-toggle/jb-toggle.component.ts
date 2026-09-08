import { Component, Input, Output, EventEmitter } from '@angular/core';

@Component({
  selector: 'jb-toggle',
  standalone: true,
  templateUrl: './jb-toggle.component.html',
  styleUrls: ['./jb-toggle.component.css']
})
export class JbToggleComponent {
  @Input() on = false;
  @Output() toggled = new EventEmitter<boolean>();
}
