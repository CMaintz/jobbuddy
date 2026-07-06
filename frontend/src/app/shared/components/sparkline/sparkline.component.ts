import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jb-sparkline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sparkline.component.html',
  styleUrls: ['./sparkline.component.css']
})
export class SparklineComponent implements OnChanges {
  @Input() data: number[] = [];
  @Input() width = 100;
  @Input() height = 28;
  @Input() color = 'var(--jb-accent)';
  @Input() showFill = true;

  linePath = '';
  areaPath = '';
  lastPoint: [number, number] | null = null;

  ngOnChanges(): void {
    if (!this.data.length) return;
    const max = Math.max(...this.data, 1);
    const step = this.data.length > 1 ? this.width / (this.data.length - 1) : 0;
    const points: [number, number][] = this.data.map((v, i) => {
      const x = i * step;
      const y = this.height - (v / (max || 1)) * (this.height - 2) - 1;
      return [x, y];
    });
    this.linePath = points.map(([x, y], i) => (i === 0 ? `M${x},${y}` : `L${x},${y}`)).join(' ');
    this.areaPath = `${this.linePath} L${this.width},${this.height} L0,${this.height} Z`;
    this.lastPoint = points[points.length - 1];
  }
}
