import { Component, Input, Output, EventEmitter, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';
import { NoticeTone } from '../../services/notify.service';

@Component({
  selector: 'jb-toast',
  imports: [JbIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
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
      // The host hides the toast by clearing `message`; a write to the input from here
      // would not render under OnPush.
      this.timer = setTimeout(() => this.dismissed.emit(), 2800);
    }
  }
}
