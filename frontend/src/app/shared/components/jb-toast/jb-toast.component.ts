import { Component, Input, Output, EventEmitter, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';
import { NoticeTone } from '../../services/notify.service';

@Component({
  selector: 'jb-toast',
  imports: [JbIconComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './jb-toast.component.html'
})
export class JbToastComponent implements OnChanges {
  @Input() message = '';
  @Input() tone: NoticeTone = 'success';
  @Output() dismissed = new EventEmitter<void>();
  private timer: ReturnType<typeof setTimeout> | undefined;

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
