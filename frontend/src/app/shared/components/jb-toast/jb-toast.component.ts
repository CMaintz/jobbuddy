import { Component, Input, Output, EventEmitter, OnChanges } from '@angular/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-toast',
  standalone: true,
  imports: [JbIconComponent],
  template: `
    @if (message) {
      <div style="position:fixed;bottom:20px;left:50%;transform:translateX(-50%);
        background:var(--jb-surface-2);color:var(--jb-text);border:1px solid var(--jb-border-strong);
        border-radius:8px;padding:10px 14px 10px 12px;display:flex;align-items:center;gap:10px;
        font-size:12.5px;z-index:9999;box-shadow:0 14px 40px rgba(0,0,0,0.4);max-width:min(420px,92vw);">
        <div style="width:18px;height:18px;border-radius:50%;
          background:var(--jb-success-soft);border:1px solid rgba(74,222,128,0.4);color:var(--jb-success);
          display:flex;align-items:center;justify-content:center;">
          <jb-icon name="check" [size]="11" [strokeWidth]="2.5" />
        </div>
        <span>{{ message }}</span>
      </div>
    }
  `
})
export class JbToastComponent implements OnChanges {
  @Input() message = '';
  @Output() dismissed = new EventEmitter<void>();
  private timer: any;

  ngOnChanges(): void {
    clearTimeout(this.timer);
    if (this.message) {
      this.timer = setTimeout(() => {
        this.message = '';
        this.dismissed.emit();
      }, 2800);
    }
  }
}
