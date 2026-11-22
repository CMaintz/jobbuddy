import {
  ChangeDetectionStrategy, Component, DestroyRef, ElementRef, InjectionToken, NgZone,
  afterNextRender, computed, effect, inject, input, viewChild,
} from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { BuddyPlayer } from './buddy-player';
import { BuddyClip, BuddyMood, moodPlaylist, resolveClip } from './buddy-sprites';

/** Whole-number zoom only, so every sprite pixel stays a square of screen pixels. */
export type BuddyScale = 1 | 2 | 3 | 4;

/** Read once per Buddy; a provider so tests can choose. */
export const BUDDY_REDUCED_MOTION = new InjectionToken<() => boolean>('BUDDY_REDUCED_MOTION', {
  providedIn: 'root',
  factory: () => () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches,
});

/** The neutral pose: idle frame 0, which every clip starts and ends one step from. */
const STILL = resolveClip('idle');

function paint(el: HTMLElement, clip: BuddyClip, frame: number, scale: number): void {
  const s = el.style;
  s.left = `${-clip.offset[0] * scale}px`;
  s.top = `${-clip.offset[1] * scale}px`;
  s.width = `${clip.width * scale}px`;
  s.height = `${clip.height * scale}px`;
  s.backgroundImage = `url(${clip.src})`;
  s.backgroundSize = `${clip.sheetWidth * scale}px ${clip.sheetHeight * scale}px`;
  s.backgroundPosition = `${-clip.width * frame * scale}px ${-clip.y * scale}px`;
}

/** Glides towards the next frame's drop while this one shows, so the float eases instead of stepping. */
function float(el: HTMLElement, clip: BuddyClip, frame: number, scale: number): void {
  const drop = clip.bob ? clip.bob[(frame + 1) % clip.bob.length] : 0;
  el.style.transition = `transform ${clip.durations[frame]}ms linear`;
  el.style.transform = `translateY(${drop * scale}px)`;
}

/**
 * Buddy the axolotl, JobBuddy's mascot, playing the clips of a mood. Under reduced motion he holds
 * his neutral pose. His box is always the idle frame's size, so nothing around him moves when he
 * loads or changes clip; clips that roam further draw past it.
 */
@Component({
  selector: 'jb-buddy',
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span #sprite class="jb-buddy-sprite" [attr.role]="alt() ? 'img' : null"
    [attr.aria-label]="alt() ? ((alt() ?? '') | translate) : null"></span>`,
  host: {
    '[style.width.px]': 'box().width',
    '[style.height.px]': 'box().height',
    '[attr.aria-hidden]': "alt() ? null : 'true'",
  },
  styles: `
    :host { position: relative; display: inline-block; flex: none; vertical-align: middle; }
    .jb-buddy-sprite {
      position: absolute; background-repeat: no-repeat; image-rendering: pixelated; pointer-events: none;
    }
  `,
})
export class BuddyComponent {
  readonly mood = input<BuddyMood>('idle');
  readonly scale = input<BuddyScale>(2);
  /** Translation key for his alt text; null when a labelled parent (a button) already names him. */
  readonly alt = input<string | null>('buddy.alt');

  protected readonly box = computed(() => ({ width: STILL.width * this.scale(), height: STILL.height * this.scale() }));
  private readonly sprite = viewChild.required<ElementRef<HTMLElement>>('sprite');
  private readonly reducedMotion = inject(BUDDY_REDUCED_MOTION);
  private readonly zone = inject(NgZone);
  private player?: BuddyPlayer<BuddyClip>;
  private playing?: BuddyMood;
  /** Set once the reduced-motion pose is up; before that the sprite may not be rendered yet. */
  private still = false;

  constructor() {
    afterNextRender(() => this.start());
    effect(() => this.react(this.mood()));
    // The player reads scale() on every frame; the still pose is painted once, so it needs a nudge.
    effect(() => {
      const scale = this.scale();
      if (this.still) paint(this.sprite().nativeElement, STILL, 0, scale);
    });
    inject(DestroyRef).onDestroy(() => this.player?.stop());
  }

  private start(): void {
    const el = this.sprite().nativeElement;
    if (this.reducedMotion()) {
      this.still = true;
      paint(el, STILL, 0, this.scale());
      return;
    }
    // Frames tick every 50-220 ms for as long as he is on screen; inside the zone each one would
    // run change detection for the whole app.
    this.zone.runOutsideAngular(() => {
      this.player = new BuddyPlayer<BuddyClip>({
        draw: (clip, frame) => {
          paint(el, clip, frame, this.scale());
          float(el, clip, frame, this.scale());
        },
        schedule: (next, ms) => {
          const id = setTimeout(next, ms);
          return () => clearTimeout(id);
        },
      });
      this.react(this.mood());
    });
  }

  /** A reaction (the wave, the head-shake) plays once per change of mood, never on a repeat. */
  private react(mood: BuddyMood): void {
    if (!this.player || mood === this.playing) return;
    this.playing = mood;
    this.player.play(moodPlaylist(mood));
  }
}
