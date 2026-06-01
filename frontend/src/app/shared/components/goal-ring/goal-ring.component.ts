import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-goal-ring',
  standalone: true,
  template: `
    <svg [attr.width]="size" [attr.height]="size" style="display:block;">
      <circle [attr.cx]="size/2" [attr.cy]="size/2" [attr.r]="radius"
        fill="none" stroke="var(--jb-surface-3)" [attr.stroke-width]="strokeW" />
      <circle [attr.cx]="size/2" [attr.cy]="size/2" [attr.r]="radius"
        fill="none" stroke="var(--jb-accent)" [attr.stroke-width]="strokeW"
        [attr.stroke-dasharray]="circumference"
        [attr.stroke-dashoffset]="offset"
        stroke-linecap="round"
        style="transform:rotate(-90deg);transform-origin:center;" />
      <text [attr.x]="size/2" [attr.y]="size/2 + 1" text-anchor="middle" dominant-baseline="central"
        style="font-size:16px;font-weight:600;fill:var(--jb-text);" class="num">
        {{ current }}
      </text>
      <text [attr.x]="size/2" [attr.y]="size/2 + 16" text-anchor="middle"
        style="font-size:9px;fill:var(--jb-text-dim);">
        / {{ goal }}
      </text>
    </svg>
  `,
  styles: [`:host { display: inline-flex; }`]
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
