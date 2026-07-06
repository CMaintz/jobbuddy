import { Component, Input, Output, EventEmitter, OnChanges } from '@angular/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-toast',
  standalone: true,
  imports: [JbIconComponent],
  templateUrl: './jb-toast.component.html'
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
