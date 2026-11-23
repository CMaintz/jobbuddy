import { BuddyManifest, moodPlaylist, resolveClip } from './buddy-sprites';

const entry = (atlasY: number | undefined, extra: object = {}) => ({
  sheet: 'own-sheet.png', frame_width: 37, frame_height: 49, frames: 2, durations_ms: [100, 100],
  ...(atlasY === undefined ? {} : { atlas_y: atlasY }), ...extra,
});

const MANIFEST: BuddyManifest = {
  'front-idle': entry(0),
  'front-look-around': entry(49),
  'front-wave': entry(98),
  'front-swim': entry(147, { frame_width: 72, frame_height: 72, offset: [35, 7] }),
};

describe('Buddy sprites', () => {
  it('falls back to a clip the generator has today when the preferred one is missing', () => {
    expect(resolveClip(['think', 'look-around'], MANIFEST).name).toBe('front-look-around');
  });

  it('uses a newly generated clip as soon as the manifest has it', () => {
    const withThink = { ...MANIFEST, 'front-think': entry(196) };
    expect(resolveClip(['think', 'look-around'], withThink).name).toBe('front-think');
  });

  it('draws atlas clips from the atlas row and others from their own sheet', () => {
    expect(resolveClip('wave', MANIFEST)).toEqual(jasmine.objectContaining({
      src: 'assets/buddy/front-atlas.png', y: 98, sheetWidth: 72 * 2, sheetHeight: 147 + 72,
    }));
    const own = { 'front-idle': entry(undefined) };
    expect(resolveClip('idle', own)).toEqual(jasmine.objectContaining({
      src: 'assets/buddy/own-sheet.png', y: 0, sheetWidth: 37 * 2, sheetHeight: 49,
    }));
  });

  it('keeps a roaming clip anchored where the idle frame sits', () => {
    expect(resolveClip('swim', MANIFEST).offset).toEqual([35, 7]);
  });

  it('never leaves Buddy blank: a slot with no clip at all falls back to the idle', () => {
    expect(resolveClip(['oops'], MANIFEST).name).toBe('front-idle');
  });

  it('opens a reaction mood with its reaction before the loop', () => {
    const cheer = moodPlaylist('cheer', MANIFEST);
    expect(cheer.enter.map(c => c.name)).toEqual(['front-wave']);
    expect(cheer.loop.map(c => c.name)).toContain('front-idle');
  });
});
