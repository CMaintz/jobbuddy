import { Directive, ElementRef, OnDestroy, effect, inject } from '@angular/core';
import { ResumeStateService } from '../services/resume-state.service';

/**
 * Attached to the photo <img> in preview layouts. Applies the photoSize and
 * photoPlacement settings, and lets the user drag on the photo to resize it
 * (Canva-style) — dragging away from the top-left corner grows it.
 */
@Directive({ selector: 'img[rbResumePhoto]', standalone: true })
export class ResumePhotoDirective implements OnDestroy {
  private el = inject(ElementRef<HTMLImageElement>).nativeElement;
  private state = inject(ResumeStateService);

  private dragStart: { x: number; y: number; size: number } | null = null;

  private onPointerDown = (e: PointerEvent) => {
    const size = this.state.settings().photoSize ?? this.el.offsetWidth;
    this.dragStart = { x: e.clientX, y: e.clientY, size };
    this.el.setPointerCapture(e.pointerId);
    e.preventDefault();
  };

  private onPointerMove = (e: PointerEvent) => {
    if (!this.dragStart) return;
    const delta = Math.max(e.clientX - this.dragStart.x, e.clientY - this.dragStart.y);
    const next = Math.round(Math.min(200, Math.max(40, this.dragStart.size + delta)));
    this.state.updateSettings({ photoSize: next });
  };

  private onPointerUp = () => {
    this.dragStart = null;
  };

  constructor() {
    this.el.style.cursor = 'nwse-resize';
    this.el.title = 'Drag to resize';
    this.el.addEventListener('pointerdown', this.onPointerDown);
    this.el.addEventListener('pointermove', this.onPointerMove);
    this.el.addEventListener('pointerup', this.onPointerUp);
    this.el.addEventListener('pointercancel', this.onPointerUp);

    effect(() => {
      const s = this.state.settings();
      if (s.photoSize) {
        this.el.style.width = `${s.photoSize}px`;
        this.el.style.height = `${s.photoSize}px`;
      } else {
        this.el.style.width = '';
        this.el.style.height = '';
      }
      // In row-flex headers this swaps the photo's side; harmless elsewhere
      this.el.style.order = s.photoPlacement === 'left' ? '-1' : s.photoPlacement === 'right' ? '99' : '';
    });
  }

  ngOnDestroy(): void {
    this.el.removeEventListener('pointerdown', this.onPointerDown);
    this.el.removeEventListener('pointermove', this.onPointerMove);
    this.el.removeEventListener('pointerup', this.onPointerUp);
    this.el.removeEventListener('pointercancel', this.onPointerUp);
  }
}
