import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { BuddyComponent, BuddyScale } from './buddy.component';
import { BuddyMood } from './buddy-sprites';

/** Buddy beside a status line, for AI waits long enough that she should know it is working. */
@Component({
  selector: 'jb-buddy-wait',
  imports: [TranslateModule, BuddyComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-3" role="status">
      <jb-buddy [mood]="mood()" [scale]="scale()" [alt]="null" />
      <div class="min-w-0">
        <div class="text-[15px] font-medium">{{ heading() | translate }}</div>
        @if (detail(); as detail) {
          <div class="text-sm text-jb-text-dim">{{ detail | translate }}</div>
        }
      </div>
    </div>
  `,
})
export class BuddyWaitComponent {
  /** Translation keys. */
  readonly heading = input.required<string>();
  readonly detail = input<string>();
  readonly mood = input<BuddyMood>('thinking');
  readonly scale = input<BuddyScale>(2);
}
