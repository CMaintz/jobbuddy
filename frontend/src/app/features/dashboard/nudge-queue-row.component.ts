import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { QueueItem } from './nudge-queue-item';

/**
 * One row of the dashboard's "today" queue. The time column is sized for "i morgen" and
 * "tomorrow" on one line; any narrower and the day word wraps or runs into the title.
 */
@Component({
  selector: 'app-nudge-queue-row',
  imports: [TranslateModule, JbPillComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid gap-3 rounded-md items-center grid-cols-[60px_1fr_auto] p-[9px_10px]"
      [style.background]="item().hot ? 'rgba(248,113,113,0.06)' : 'transparent'"
      [style.border]="item().hot ? '1px solid rgba(248,113,113,0.18)' : '1px solid transparent'">
      <div class="mono text-xs whitespace-nowrap"
        [style.color]="item().hot ? 'var(--jb-danger)' : 'var(--jb-text-dim)'">{{ item().time | translate }}</div>
      <div class="flex flex-col gap-px">
        <div class="text-base">{{ item().what | translate: item().whatParams }}</div>
        <div class="text-xs text-jb-text-dim">{{ item().detail }}</div>
      </div>
      <jb-pill [tone]="item().tone">{{ item().tag | translate }}</jb-pill>
    </div>
  `,
})
export class NudgeQueueRowComponent {
  readonly item = input.required<QueueItem>();
}
