import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jb-heatmap',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="display:flex;gap:3px;">
      <div *ngFor="let week of weeks" style="display:flex;flex-direction:column;gap:3px;">
        <div *ngFor="let day of week"
          [style.width.px]="cellSize" [style.height.px]="cellSize"
          [style.border-radius.px]="2"
          [style.background]="cellColor(day)">
        </div>
      </div>
    </div>
  `,
  styles: [`:host { display: block; }`]
})
export class HeatmapComponent implements OnChanges {
  @Input() data: number[] = [];
  @Input() weekCount = 14;
  @Input() cellSize = 11;

  weeks: number[][] = [];

  ngOnChanges(): void {
    this.buildWeeks();
  }

  private buildWeeks(): void {
    const values = this.data.length > 0 ? this.data : this.generateDemoData();
    this.weeks = [];
    for (let w = 0; w < this.weekCount; w++) {
      const week: number[] = [];
      for (let d = 0; d < 7; d++) {
        const idx = w * 7 + d;
        week.push(idx < values.length ? values[idx] : 0);
      }
      this.weeks.push(week);
    }
  }

  cellColor(v: number): string {
    if (v === 0) return 'var(--jb-surface-3)';
    if (v < 2) return 'rgba(245,166,35,0.32)';
    if (v < 4) return 'rgba(245,166,35,0.66)';
    return 'var(--jb-accent)';
  }

  private generateDemoData(): number[] {
    const data: number[] = [];
    for (let i = 0; i < this.weekCount * 7; i++) {
      data.push(Math.random() < 0.3 ? 0 : Math.floor(Math.random() * 5));
    }
    return data;
  }
}
