import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

/**
 * Modal shell: backdrop, panel, header with close button, scrollable body,
 * and an optional footer bar.
 *
 * Usage:
 * ```html
 * <jb-modal [width]="640" (closed)="showIt.set(false)">
 *   <ng-container jb-modal-header>…title row content…</ng-container>
 *   …body…
 *   <ng-container jb-modal-footer>…action buttons…</ng-container>
 * </jb-modal>
 * ```
 */
@Component({
  selector: 'jb-modal',
  standalone: true,
  imports: [CommonModule, JbIconComponent],
  template: `
    <div (click)="closed.emit()" class="fixed inset-0 z-[200] flex items-center justify-center" style="background:rgba(0,0,0,0.55);">
      <div (click)="$event.stopPropagation()"
           class="max-h-[85vh] bg-jb-surface border border-jb-border-strong rounded-[10px] flex flex-col"
           [style.width]="'min(' + width + 'px, 92vw)'"
           style="box-shadow:0 28px 80px rgba(0,0,0,0.5);">
        <div class="py-3.5 px-[18px] border-b border-jb-border flex items-center gap-2.5">
          <ng-content select="[jb-modal-header]" />
          <button (click)="closed.emit()" class="bg-transparent border-none text-jb-text-mid cursor-pointer p-1 ml-auto">
            <jb-icon name="x" [size]="14" />
          </button>
        </div>
        <div class="p-[18px] flex-1 overflow-auto flex flex-col gap-4 jb-scroll">
          <ng-content />
        </div>
        @if (hasFooter) {
          <div class="py-3 px-[18px] border-t border-jb-border flex items-center gap-2">
            <ng-content select="[jb-modal-footer]" />
          </div>
        }
      </div>
    </div>
  `,
})
export class JbModalComponent {
  /** Max panel width in px (clamped to 92vw). */
  @Input() width = 640;
  /** Set false for modals without an action bar. */
  @Input() hasFooter = true;
  @Output() closed = new EventEmitter<void>();
}
