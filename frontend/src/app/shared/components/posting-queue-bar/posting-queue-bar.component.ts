import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { PostingQueue } from '../../../core/chat/posting-queue';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-posting-queue-bar',
  imports: [TranslateModule, JbIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './posting-queue-bar.component.html',
})
export class PostingQueueBarComponent {
  readonly queue = inject(PostingQueue);
  private router = inject(Router);

  apply(): void {
    const posting = this.queue.current();
    if (posting) void this.router.navigate(['/apply'], { queryParams: { jobId: posting.id } });
  }
}
