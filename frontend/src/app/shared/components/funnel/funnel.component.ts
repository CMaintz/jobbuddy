import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'jb-funnel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="display:flex;flex-direction:column;gap:6px;">
      <div *ngFor="let stage of stages" style="display:flex;align-items:center;gap:12px;">
        <span style="width:70px;font-size:11px;color:var(--jb-text-mid);text-align:right;">{{ stage.label }}</span>
        <div style="flex:1;height:18px;border-radius:4px;overflow:hidden;background:var(--jb-surface-3);">
          <div [style.width.%]="(stage.value / maxVal) * 100" [style.height.px]="18"
            [style.background]="stage.color" style="border-radius:4px;transition:width .3s;"></div>
        </div>
        <span class="mono" style="font-size:11px;min-width:24px;color:var(--jb-text-mid);">{{ stage.value }}</span>
      </div>
    </div>
  `,
  styles: [`:host { display: block; }`]
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
