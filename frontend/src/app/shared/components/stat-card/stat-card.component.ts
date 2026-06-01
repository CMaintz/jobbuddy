import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-stat-card',
  standalone: true,
  imports: [CommonModule, JbIconComponent],
  template: `
    <div class="card flex flex-col gap-2 p-3.5"
      [style.border-color]="accent ? 'var(--jb-accent-border)' : 'var(--jb-border)'">
      <div class="flex items-center justify-between">
        <span class="text-xs text-jb-text-dim font-medium">{{ label }}</span>
        <jb-icon *ngIf="icon" [name]="icon" [size]="12" [style.color]="accent ? 'var(--jb-accent)' : 'var(--jb-text-dim)'" />
      </div>
      <div class="flex items-baseline gap-1">
        <span class="num text-6xl font-medium tracking-[-0.03em] leading-none"
          [style.color]="accent ? 'var(--jb-accent-2)' : 'var(--jb-text)'"
          >{{ value }}</span>
        <span *ngIf="sub" class="text-xl text-jb-text-dim font-normal">{{ sub }}</span>
        <span *ngIf="delta" class="mono text-2xs ml-auto py-px px-[5px] rounded-sm"
          [style.color]="delta.startsWith('+') ? 'var(--jb-success)' : 'var(--jb-danger)'"
          [style.background]="delta.startsWith('+') ? 'var(--jb-success-soft)' : 'var(--jb-danger-soft)'">
          {{ delta }}
        </span>
      </div>
      <ng-content />
    </div>
  `
})
export class StatCardComponent {
  @Input() label = '';
  @Input() value = '';
  @Input() sub = '';
  @Input() delta = '';
  @Input() icon = '';
  @Input() accent = false;
}
