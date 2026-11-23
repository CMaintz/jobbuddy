// Buddy for pages that run no script of their own: the desktop shell's pages (their CSP allows
// neither inline scripts nor style attributes) and the Angular boot screen, shown before any
// script has loaded. Each mood becomes CSS keyframes over the sprite atlas, timed from the
// manifest, so a regenerated atlas with shifted rows or new clips needs only a re-sync.

const VIEW = 'front-';
const DECIMALS = 3;

/**
 * The first of `names` the atlas has in a frame the size of `frame`. Lists let a mood name a clip
 * the generator may add later ahead of one it has today, so the new clip takes over on the next
 * sync with no code change. Clips drawn in a bigger frame (the swim, a celebration) need the
 * offset handling of the Angular component, so here they give way to the next name.
 */
function pickClip(manifest, names, frame) {
  const options = [names].flat();
  const fits = (m) => !frame || (m.frame_width === frame.frame_width && m.frame_height === frame.frame_height);
  const clip = options.map((n) => manifest[VIEW + n]).find((m) => m?.atlas_y !== undefined && fits(m));
  if (!clip) throw new Error(`None of ${options.join(', ')} is in the sprite atlas in the idle's frame`);
  return clip;
}

function framesOf(clip) {
  return clip.durations_ms.map((ms, i) => ({
    x: i * clip.frame_width, y: clip.atlas_y, ms, bob: clip.bob?.[i] ?? 0,
  }));
}

const round = (n) => Number(n.toFixed(DECIMALS));
const scaled = (n) => (n === 0 ? '0' : `calc(var(--buddy-scale) * ${round(n)}px)`);

/** Concatenated frames, each stamped with the percentage of the run where it starts. */
function timeline(frames) {
  const total = frames.reduce((sum, f) => sum + f.ms, 0);
  let at = 0;
  const steps = frames.map((f) => {
    const step = { ...f, pct: round((at / total) * 100) };
    at += f.ms;
    return step;
  });
  return { total, steps };
}

function positionKeyframes(name, { steps }) {
  const rows = steps.map((s) => `  ${s.pct}% { background-position: ${scaled(-s.x)} ${scaled(-s.y)}; }`);
  return `@keyframes ${name} {\n${rows.join('\n')}\n}`;
}

/** The idle's float, eased between frames; the sprite itself never moves inside its frame. */
function bobKeyframes(name, { steps }) {
  const rows = [...steps, { pct: 100, bob: steps[0].bob }]
    .map((s) => `  ${s.pct}% { transform: translateY(${scaled(s.bob)}); }`);
  return `@keyframes ${name} {\n${rows.join('\n')}\n}`;
}

const floats = ({ steps }) => steps.some((s) => s.bob !== 0);

/** One run of clips: its keyframes and its entries for the `animation` list. */
function run(name, run, { delay, repeat }) {
  const at = delay ? ` ${delay}ms` : '';
  const keyframes = [positionKeyframes(name, run)];
  const animations = [`${name} ${run.total}ms step-end${at} ${repeat}`];
  if (floats(run)) {
    keyframes.push(bobKeyframes(`${name}-bob`, run));
    animations.push(`${name}-bob ${run.total}ms linear${at} ${repeat}`);
  }
  return { keyframes, animations };
}

/**
 * A mood plays `enter` once, then repeats `loop`. Both name one run each, so the loop's delay is
 * the whole enter; later animations win, so each run shows only while it is the one playing.
 */
function moodCss(mood, { enter = [], loop }, pick) {
  const timelineOf = (slots) => timeline(slots.flatMap((names) => framesOf(pick(names))));
  const parts = [];
  let delay = 0;
  if (enter.length) {
    const opening = timelineOf(enter);
    parts.push(run(`buddy-${mood}-enter`, opening, { delay: 0, repeat: 1 }));
    delay = opening.total;
  }
  parts.push(run(`buddy-${mood}`, timelineOf(loop), { delay, repeat: 'infinite' }));
  const animations = parts.flatMap((p) => p.animations).join(', ');
  return [...parts.flatMap((p) => p.keyframes), `.buddy--${mood} .buddy__sprite { animation: ${animations}; }`];
}

/** Atlas size in sprite pixels, from the manifest rows it holds. */
export function atlasSize(manifest) {
  const rows = Object.values(manifest).filter((m) => m.atlas_y !== undefined);
  return {
    width: Math.max(...rows.map((m) => m.frame_width * m.frames)),
    height: Math.max(...rows.map((m) => m.atlas_y + m.frame_height)),
  };
}

function baseCss(idle, atlas, size) {
  const scales = [1, 2, 3, 4].map((s) => `.buddy--x${s} { --buddy-scale: ${s}; }`);
  return [
    '.buddy {',
    '  --buddy-scale: 2;',
    '  position: relative;',
    '  display: block;',
    `  width: ${scaled(idle.frame_width)};`,
    `  height: ${scaled(idle.frame_height)};`,
    '}',
    ...scales,
    '.buddy__sprite {',
    '  position: absolute;',
    '  inset: 0;',
    `  background: url(${atlas}) no-repeat;`,
    `  background-size: ${scaled(size.width)} ${scaled(size.height)};`,
    `  background-position: 0 ${scaled(-idle.atlas_y)};`,
    '  image-rendering: pixelated;',
    '}',
    '@media (prefers-reduced-motion: reduce) {',
    '  .buddy__sprite { animation: none !important; }',
    '}',
  ];
}

/**
 * The stylesheet: `<span class="buddy buddy--x2 buddy--loading"><span class="buddy__sprite">`.
 *
 * @param {Record<string, object>} manifest brand/buddy-sprites/out/manifest.json
 * @param {{ atlas: string, moods: Record<string, { enter?: (string|string[])[], loop: (string|string[])[] }> }}
 *   options
 */
export function buddyCss(manifest, { atlas, moods }) {
  const idle = pickClip(manifest, 'idle');
  const pick = (names) => pickClip(manifest, names, idle);
  const rules = Object.entries(moods).flatMap(([mood, spec]) => moodCss(mood, spec, pick));
  return [...baseCss(idle, atlas, atlasSize(manifest)), ...rules].join('\n') + '\n';
}
