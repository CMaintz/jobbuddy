import { Component, Input } from '@angular/core';

export type PillTone = 'neutral' | 'accent' | 'success' | 'info' | 'danger' | 'violet';

@Component({
  selector: 'jb-pill',
  standalone: true,
  templateUrl: './jb-pill.component.html',
  styleUrls: ['./jb-pill.component.css']
})
export class JbPillComponent {
  @Input() tone: PillTone = 'neutral';
}
