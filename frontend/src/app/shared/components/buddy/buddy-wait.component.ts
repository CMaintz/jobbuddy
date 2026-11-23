import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { BuddyComponent, BuddyScale } from './buddy.component';
import { BuddyMood } from './buddy-sprites';

/**
 * How long the status region sits empty before its text goes in. Screen readers announce a change
 * inside a live region they already know about, not a region that arrives with its text, and
 * callers mount this component together with the wait. Same delay as the CDK's LiveAnnouncer.
 */
const ANNOUNCE_DELAY_MS = 100;

/** Buddy beside a status line, for AI waits long enough that she should know it is working. */
@Component({
  selector: 'jb-buddy-wait',
  imports: [TranslateModule, BuddyComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-3">
      <jb-buddy [mood]="mood()" [scale]="scale()" [alt]="null" />
      <!-- Shown from the first frame so the layout never waits on the announcement; the status
           region below carries the same text for screen readers. -->
      <div class="min-w-0" aria-hidden="true">
        <div class="text-[15px] font-medium">{{ heading() | translate }}</div>
        @if (detail(); as detail) {
          <div class="text-sm text-jb-text-dim">{{ detail | translate }}</div>
        }
      </div>
      <div class="sr-only" role="status">
        @if (announced()) {
          {{ heading() | translate }}
          @if (detail(); as detail) { {{ detail | translate }} }
        }
      </div>
    </div>
  `,
  // Callers size it with width classes, which an inline custom element ignores.
  styles: `:host { display: block; }`,
})
export class BuddyWaitComponent {
  /** Translation keys. */
  readonly heading = input.required<string>();
  readonly detail = input<string>();
  readonly mood = input<BuddyMood>('thinking');
  readonly scale = input<BuddyScale>(2);

  protected readonly announced = signal(false);

  constructor() {
    const id = setTimeout(() => this.announced.set(true), ANNOUNCE_DELAY_MS);
    inject(DestroyRef).onDestroy(() => clearTimeout(id));
  }
}
