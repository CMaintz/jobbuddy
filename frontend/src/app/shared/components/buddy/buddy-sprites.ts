// Buddy's clips, read from the brand sprite generator's manifest (brand/buddy-sprites/out).
// buddy-manifest.json and the images in assets/buddy are copied by desktop/scripts/sync-buddy.mjs;
// never edit them by hand, re-run the sync after regenerating the sprites.
import manifestJson from './buddy-manifest.json';

interface ManifestEntry {
  sheet: string;
  frame_width: number;
  frame_height: number;
  frames: number;
  durations_ms: number[];
  /** Present for clips the generator also stacked into the atlas. */
  atlas_y?: number;
  /** Where the idle frame's top-left sits in a roaming clip's bigger frame. */
  offset?: number[];
  bob?: number[];
}

export type BuddyManifest = Record<string, ManifestEntry>;

export interface BuddyClip {
  /** The manifest key, e.g. `front-wave`. */
  name: string;
  src: string;
  /** Sheet size, frame size, row and offset are in sprite pixels. */
  sheetWidth: number;
  sheetHeight: number;
  y: number;
  width: number;
  height: number;
  offset: [number, number];
  durations: number[];
  /** Per-frame drop the page adds, so the idle floats without the sprite moving in its frame. */
  bob?: number[];
}

/** A clip name, or names in preference order, so a clip the generator adds later takes over. */
type ClipSlot = string | readonly string[];

export type BuddyMood = 'idle' | 'thinking' | 'cheer' | 'error';

interface MoodSpec {
  /** Played once when the mood starts. */
  enter?: ClipSlot[];
  /** Repeated for as long as the mood lasts. */
  loop: ClipSlot[];
}

// think, celebrate and oops come with #349's sprites; until the synced manifest has them, the next name plays.
const MOODS: Record<BuddyMood, MoodSpec> = {
  idle: { loop: ['idle', 'blink', 'idle', 'tail-wag'] },
  thinking: { loop: [['think', 'look-around'], 'blink'] },
  cheer: { enter: [['celebrate', 'wave']], loop: ['idle', 'blink'] },
  error: { enter: [['oops', 'head-shake']], loop: ['idle', 'blink'] },
};

const ASSETS = 'assets/buddy/';
// Every view is a key prefix; the moods use the front view only.
const VIEW = 'front-';
// The generator stacks every `front-` clip into this one image, so switching clips never waits
// on a sheet the browser has not loaded yet.
const ATLAS = 'front-atlas.png';

const MANIFEST = manifestJson as BuddyManifest;

function atlasSize(manifest: BuddyManifest): { width: number; height: number } {
  const rows = Object.values(manifest).filter(m => m.atlas_y !== undefined);
  return {
    width: Math.max(...rows.map(m => m.frame_width * m.frames)),
    height: Math.max(...rows.map(m => (m.atlas_y ?? 0) + m.frame_height)),
  };
}

function toClip(name: string, m: ManifestEntry, manifest: BuddyManifest): BuddyClip {
  const inAtlas = m.atlas_y !== undefined;
  const sheet = inAtlas ? atlasSize(manifest) : { width: m.frame_width * m.frames, height: m.frame_height };
  const [x = 0, y = 0] = m.offset ?? [];
  return {
    name,
    src: ASSETS + (inAtlas ? ATLAS : m.sheet),
    sheetWidth: sheet.width,
    sheetHeight: sheet.height,
    y: m.atlas_y ?? 0,
    width: m.frame_width,
    height: m.frame_height,
    offset: [x, y],
    durations: m.durations_ms,
    bob: m.bob,
  };
}

/**
 * The first clip of `slot` the manifest has. A slot with none of them gives the idle, so a
 * renamed or dropped clip never leaves him blank.
 */
export function resolveClip(slot: ClipSlot, manifest = MANIFEST): BuddyClip {
  const names = typeof slot === 'string' ? [slot] : slot;
  const key = [...names, 'idle'].map(n => VIEW + n).find(k => manifest[k]) ?? `${VIEW}idle`;
  return toClip(key, manifest[key], manifest);
}

export interface Playlist<C> {
  enter: readonly C[];
  loop: readonly C[];
}

export function moodPlaylist(mood: BuddyMood, manifest = MANIFEST): Playlist<BuddyClip> {
  const { enter = [], loop } = MOODS[mood];
  const resolve = (slot: ClipSlot) => resolveClip(slot, manifest);
  return { enter: enter.map(resolve), loop: loop.map(resolve) };
}
