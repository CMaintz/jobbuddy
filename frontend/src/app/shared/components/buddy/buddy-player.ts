// Plays Buddy's clips frame by frame with the manifest's own timings. Every clip the brand rig
// draws starts and ends one step from the idle's neutral pose, so a new mood waits for the clip
// boundary and the cut doesn't show. The idle loop is almost five seconds, though, too long for a
// reaction to wait, so a request cuts in after URGENT_MS at most.
import type { Playlist } from './buddy-sprites';

const URGENT_MS = 400;

interface PlayerClip {
  durations: readonly number[];
}

interface PlayerOptions<C extends PlayerClip> {
  draw: (clip: C, frame: number) => void;
  /** Runs `next` after `ms`; returns a cancel. Injected so tests drive the clock. */
  schedule: (next: () => void, ms: number) => () => void;
}

/** Where in a playlist the player is. */
class Position<C extends PlayerClip> {
  private entered = 0;
  private looped = 0;
  clip: C;
  frame = 0;

  constructor(private readonly playlist: Playlist<C>) {
    this.clip = this.next();
  }

  get atEnd(): boolean {
    return this.frame === this.clip.durations.length - 1;
  }

  get ms(): number {
    return this.clip.durations[this.frame];
  }

  advance(): void {
    if (this.atEnd) {
      this.clip = this.next();
      this.frame = 0;
    } else {
      this.frame++;
    }
  }

  private next(): C {
    const { enter, loop } = this.playlist;
    if (this.entered < enter.length) return enter[this.entered++];
    return loop[this.looped++ % loop.length];
  }
}

export class BuddyPlayer<C extends PlayerClip> {
  private at?: Position<C>;
  private pending?: Playlist<C>;
  /** Time shown since `pending` was asked for. */
  private waited = 0;
  private cancel?: () => void;

  constructor(private readonly o: PlayerOptions<C>) {}

  /** Plays the playlist's enter clips once, then loops it. The first call starts at once. */
  play(playlist: Playlist<C>): void {
    if (this.at) {
      this.pending = playlist;
      this.waited = 0;
      return;
    }
    this.show(new Position(playlist));
  }

  stop(): void {
    this.cancel?.();
    this.cancel = undefined;
  }

  private show(at: Position<C>): void {
    this.at = at;
    this.o.draw(at.clip, at.frame);
    this.cancel = this.o.schedule(() => this.tick(at), at.ms);
  }

  private tick(at: Position<C>): void {
    this.waited += at.ms;
    if (this.pending && (at.atEnd || this.waited >= URGENT_MS)) {
      const next = new Position(this.pending);
      this.pending = undefined;
      this.show(next);
      return;
    }
    at.advance();
    this.show(at);
  }
}
