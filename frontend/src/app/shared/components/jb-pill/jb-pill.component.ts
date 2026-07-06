import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-pill',
  standalone: true,
  templateUrl: './jb-pill.component.html',
  styleUrls: ['./jb-pill.component.css']
})
export class JbPillComponent {
  @Input() tone: 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet' = 'neutral';
}
