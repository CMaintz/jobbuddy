import { Component, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityStreakService } from '../../../core/activity-streak.service';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-streak-badge',
  imports: [TranslateModule, JbIconComponent],
  template: `
    @if (streak.days() > 0) {
      <div class="flex items-center gap-2.5 rounded-md py-1.5 px-2 text-base text-jb-text-mid">
        <jb-icon name="flame" [size]="14" class="text-jb-accent" />
        <span>{{ 'nav.streak' | translate:{ count: streak.days() } }}</span>
      </div>
    }
  `,
})
export class StreakBadgeComponent {
  protected streak = inject(ActivityStreakService);
}
