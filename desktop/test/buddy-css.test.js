'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');

const load = () => import('../scripts/buddy-css.mjs');

const clip = (y, durations, extra = {}) => ({
  sheet: 'x.png', frame_width: 37, frame_height: 49, frames: durations.length, durations_ms: durations, atlas_y: y,
  ...extra,
});

const MANIFEST = {
  'front-idle': clip(0, [100, 100], { bob: [0, 2] }),
  'front-look-around': clip(49, [90, 90, 220, 220]),
  'front-head-shake': clip(98, [65, 65]),
  'side-swim': { sheet: 'side.png', frame_width: 41, frame_height: 32, frames: 1, durations_ms: [130] },
};

/** The keyframe percentages of one @keyframes block, in order. */
function stops(css, name) {
  const block = css.match(new RegExp(`@keyframes ${name} \\{([^@]*?)\\n\\}`))[1];
  return [...block.matchAll(/([\d.]+)% \{/g)].map((m) => Number(m[1]));
}

test('each frame starts at its share of the clip time, not an equal step', async () => {
  const { buddyCss } = await load();
  const css = buddyCss(MANIFEST, { atlas: 'atlas.png', moods: { wait: { loop: ['look-around'] } } });
  // 90, 90, 220, 220 ms: the long frames hold twice as long as an equal split would give them.
  assert.deepEqual(stops(css, 'buddy-wait'), [0, 14.516, 29.032, 64.516]);
});

test('a clip the manifest lacks falls back to the next name, and a new clip is picked up', async () => {
  const { buddyCss } = await load();
  const moods = { wait: { loop: [['thinking', 'look-around']] } };
  assert.match(buddyCss(MANIFEST, { atlas: 'atlas.png', moods }), /calc\(var\(--buddy-scale\) \* -49px\)/);

  const withThinking = { ...MANIFEST, 'front-thinking': clip(147, [100]) };
  assert.match(buddyCss(withThinking, { atlas: 'atlas.png', moods }), /calc\(var\(--buddy-scale\) \* -147px\)/);
});

test('a clip drawn in a bigger frame gives way to the next name', async () => {
  const { buddyCss } = await load();
  const withThink = { ...MANIFEST, 'front-think': clip(147, [100], { frame_height: 52, offset: [0, 3] }) };
  const css = buddyCss(withThink, { atlas: 'atlas.png', moods: { wait: { loop: [['think', 'look-around']] } } });
  assert.match(css, /calc\(var\(--buddy-scale\) \* -49px\)/);
  assert.doesNotMatch(css, /-147px/);
});

test('a mood with no usable clip fails the sync instead of leaving Buddy blank', async () => {
  const { buddyCss } = await load();
  assert.throws(() => buddyCss(MANIFEST, { atlas: 'atlas.png', moods: { x: { loop: ['swim'] } } }), /swim/);
});

test('the loop waits for the opening clip to finish', async () => {
  const { buddyCss } = await load();
  const css = buddyCss(MANIFEST, {
    atlas: 'atlas.png',
    moods: { error: { enter: ['head-shake', 'head-shake'], loop: ['idle'] } },
  });
  assert.match(css, /buddy-error-enter 260ms step-end 1/);
  assert.match(css, /buddy-error 200ms step-end 260ms infinite/);
});

test('reduced motion stops every animation, leaving the neutral frame', async () => {
  const { buddyCss } = await load();
  const css = buddyCss(MANIFEST, { atlas: 'atlas.png', moods: { wait: { loop: ['idle'] } } });
  const reduced = css.match(/@media \(prefers-reduced-motion: reduce\) \{([^}]*\})/);
  assert.ok(reduced, 'no reduced-motion rule');
  assert.match(reduced[1], /\.buddy__sprite \{ animation: none !important; \}/);
  assert.match(css, /\.buddy__sprite \{[^}]*background-position: 0 0;/);
});
