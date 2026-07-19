import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

const VIEWPORT = 280;
const OUTPUT = 400;

/**
 * Square crop dialog with drag-to-pan and zoom, previewing a circle or square
 * mask. Emits the cropped image as a JPEG data URL.
 */
@Component({
  selector: 'app-photo-crop-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="fixed inset-0 z-[300] flex items-center justify-center" style="background:rgba(0,0,0,0.6);" (click)="cancelled.emit()">
      <div class="bg-jb-surface border border-jb-border-strong rounded-[10px] p-4 flex flex-col gap-3"
           style="box-shadow:0 28px 80px rgba(0,0,0,0.5);" (click)="$event.stopPropagation()">
        <div class="text-md font-medium">Crop photo — drag to move, slide to zoom</div>

        <div #viewport class="relative overflow-hidden select-none bg-black/40 touch-none"
             [style.width.px]="viewportSize" [style.height.px]="viewportSize"
             class="cursor-grab rounded-lg"
             (pointerdown)="startPan($event)" (pointermove)="pan($event)"
             (pointerup)="endPan()" (pointercancel)="endPan()">
          @if (imageLoaded()) {
            <img [src]="src" alt="" class="absolute top-0 left-0 max-w-none pointer-events-none"
              [style.width.px]="drawWidth()" [style.height.px]="drawHeight()"
              [style.transform]="'translate(' + offsetX() + 'px,' + offsetY() + 'px)'" />
          }
          <!-- Mask overlay -->
          <div class="absolute inset-0 pointer-events-none"
            [style.border-radius]="shape === 'circle' ? '50%' : shape === 'rounded' ? '24px' : '0'"
            class="shadow-[0_0_0_999px_rgba(0,0,0,0.55)] border border-[rgba(255,255,255,0.6)]"></div>
        </div>

        <div class="flex items-center gap-2">
          <span class="text-xs text-jb-text-dim">Zoom</span>
          <input type="range" min="1" max="3" step="0.02" class="flex-1" style="accent-color:var(--jb-accent);"
            [ngModel]="zoom()" (ngModelChange)="setZoom(+$event)" />
        </div>

        <div class="flex justify-end gap-2">
          <button class="btn btn-sm" (click)="cancelled.emit()">Cancel</button>
          <button class="btn btn-primary btn-sm" (click)="apply()">Apply crop</button>
        </div>
      </div>
    </div>
  `,
})
export class PhotoCropDialogComponent implements OnInit {
  @ViewChild('viewport', { static: true }) viewport!: ElementRef<HTMLElement>;

  /** Source image (object URL or data URL). */
  @Input({ required: true }) src = '';
  @Input() shape: 'square' | 'rounded' | 'circle' = 'circle';
  @Output() cropped = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  viewportSize = VIEWPORT;
  imageLoaded = signal(false);
  zoom = signal(1);
  offsetX = signal(0);
  offsetY = signal(0);
  drawWidth = signal(0);
  drawHeight = signal(0);

  private image = new Image();
  private baseScale = 1;
  private panStart: { x: number; y: number; ox: number; oy: number } | null = null;

  ngOnInit(): void {
    this.image.onload = () => {
      // Base scale: cover the viewport at zoom 1
      this.baseScale = VIEWPORT / Math.min(this.image.naturalWidth, this.image.naturalHeight);
      this.applyTransform();
      this.imageLoaded.set(true);
    };
    this.image.src = this.src;
  }

  setZoom(zoom: number): void {
    this.zoom.set(zoom);
    this.applyTransform();
  }

  startPan(e: PointerEvent): void {
    this.panStart = { x: e.clientX, y: e.clientY, ox: this.offsetX(), oy: this.offsetY() };
    this.viewport.nativeElement.setPointerCapture(e.pointerId);
  }

  pan(e: PointerEvent): void {
    if (!this.panStart) return;
    this.offsetX.set(this.clampX(this.panStart.ox + e.clientX - this.panStart.x));
    this.offsetY.set(this.clampY(this.panStart.oy + e.clientY - this.panStart.y));
  }

  endPan(): void {
    this.panStart = null;
  }

  private applyTransform(): void {
    const scale = this.baseScale * this.zoom();
    this.drawWidth.set(this.image.naturalWidth * scale);
    this.drawHeight.set(this.image.naturalHeight * scale);
    this.offsetX.set(this.clampX(this.offsetX()));
    this.offsetY.set(this.clampY(this.offsetY()));
  }

  private clampX(x: number): number {
    return Math.min(0, Math.max(VIEWPORT - this.drawWidth(), x));
  }

  private clampY(y: number): number {
    return Math.min(0, Math.max(VIEWPORT - this.drawHeight(), y));
  }

  apply(): void {
    const scale = this.baseScale * this.zoom();
    const canvas = document.createElement('canvas');
    canvas.width = OUTPUT;
    canvas.height = OUTPUT;
    const ctx = canvas.getContext('2d')!;
    ctx.drawImage(
      this.image,
      -this.offsetX() / scale, -this.offsetY() / scale,
      VIEWPORT / scale, VIEWPORT / scale,
      0, 0, OUTPUT, OUTPUT,
    );
    this.cropped.emit(canvas.toDataURL('image/jpeg', 0.88));
  }
}
