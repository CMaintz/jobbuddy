import { BuddyPlayer } from './buddy-player';

interface Clip { name: string; durations: number[] }

const clip = (name: string, frames: number, ms: number): Clip =>
  ({ name, durations: Array<number>(frames).fill(ms) });

const IDLE = clip('idle', 48, 100);
const BLINK = clip('blink', 1, 100);
const WAVE = clip('wave', 2, 50);
const LOOK = clip('look', 3, 90);

/** A clock the test advances; the player only ever has one frame pending. */
class FakeClock {
  private pending?: { run: () => void; ms: number };
  schedule = (run: () => void, ms: number) => {
    this.pending = { run, ms };
    return () => (this.pending = undefined);
  };
  advance(ms: number): void {
    let left = ms;
    while (this.pending && this.pending.ms <= left) {
      const { run, ms: due } = this.pending;
      left -= due;
      this.pending = undefined;
      run();
    }
    if (this.pending) this.pending.ms -= left;
  }
  get idle(): boolean {
    return !this.pending;
  }
}

describe('BuddyPlayer', () => {
  let clock: FakeClock;
  let shown: string[];

  function start(enter: Clip[], loop: Clip[]): BuddyPlayer<Clip> {
    const player = new BuddyPlayer<Clip>({
      draw: (c, frame) => shown.push(`${c.name}:${frame}`),
      schedule: clock.schedule,
    });
    player.play({ enter, loop });
    return player;
  }

  beforeEach(() => {
    clock = new FakeClock();
    shown = [];
  });

  it('plays a reaction once, then settles into the loop', () => {
    start([WAVE], [BLINK, LOOK]);
    clock.advance(50 + 50 + 100 + 3 * 90 + 100);
    expect(shown).toEqual(['wave:0', 'wave:1', 'blink:0', 'look:0', 'look:1', 'look:2', 'blink:0', 'look:0']);
  });

  it('lets a new mood cut into a long idle within a moment instead of waiting out the loop', () => {
    const player = start([], [IDLE]);
    clock.advance(1000);
    player.play({ enter: [WAVE], loop: [LOOK] });
    shown = [];
    clock.advance(500);
    expect(shown).toContain('wave:0');
  });

  it('switches on a clip boundary when the clip ends before the cut-in limit', () => {
    const player = start([], [LOOK]);
    clock.advance(90);
    player.play({ enter: [], loop: [BLINK] });
    clock.advance(2 * 90);
    expect(shown).toEqual(['look:0', 'look:1', 'look:2', 'blink:0']);
  });

  it('stops for good when destroyed', () => {
    const player = start([], [IDLE]);
    player.stop();
    expect(clock.idle).toBeTrue();
  });
});
