import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jb-funnel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './funnel.component.html',
  styleUrls: ['./funnel.component.css']
})
export class FunnelComponent {
  @Input() stages: { label: string; value: number; color: string }[] = [
    { label: 'Saved', value: 42, color: 'var(--jb-text-dim)' },
    { label: 'Applied', value: 38, color: 'var(--jb-info)' },
    { label: 'Screen', value: 12, color: 'var(--jb-violet)' },
    { label: 'Interview', value: 6, color: 'var(--jb-accent)' },
    { label: 'Offer', value: 2, color: 'var(--jb-success)' },
  ];

  get maxVal(): number {
    return Math.max(...this.stages.map(s => s.value), 1);
  }
}
