import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-goal-ring',
  standalone: true,
  templateUrl: './goal-ring.component.html',
  styleUrls: ['./goal-ring.component.css']
})
export class GoalRingComponent {
  @Input() current = 7;
  @Input() goal = 10;
  @Input() size = 72;
  @Input() strokeW = 5;

  get radius(): number { return (this.size - this.strokeW) / 2; }
  get circumference(): number { return 2 * Math.PI * this.radius; }
  get offset(): number {
    const pct = Math.min(this.current / (this.goal || 1), 1);
    return this.circumference * (1 - pct);
  }
}
