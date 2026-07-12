import { Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Dropdown shell: projected trigger + floating panel with a click-away backdrop.
 *
 * Usage:
 * ```html
 * <jb-dropdown #dd [width]="240">
 *   <jb-button jb-trigger small icon="download">Export</jb-button>
 *   <button class="jb-menu-item" (click)="doThing(); dd.close()">…</button>
 * </jb-dropdown>
 * ```
 */
@Component({
  selector: 'jb-dropdown',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="relative">
      <div (click)="toggle()">
        <ng-content select="[jb-trigger]" />
      </div>
      @if (open()) {
        <div class="fixed inset-0 z-[5]" (click)="close()"></div>
        <div class="absolute top-[calc(100%+6px)] z-[6] p-1.5 bg-jb-surface border border-jb-border-strong rounded-lg"
             [style.width.px]="width"
             [class.right-0]="align === 'right'"
             [class.left-0]="align === 'left'"
             style="box-shadow:0 14px 40px rgba(0,0,0,0.4);">
          <ng-content />
        </div>
      }
    </div>
  `,
})
export class JbDropdownComponent {
  /** Panel width in px. */
  @Input() width = 240;
  /** Which edge of the trigger the panel aligns to. */
  @Input() align: 'left' | 'right' = 'right';

  open = signal(false);

  toggle(): void { this.open.set(!this.open()); }
  close(): void { this.open.set(false); }
}
