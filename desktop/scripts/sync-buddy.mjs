// Copies Buddy's sprite output (brand/buddy-sprites/out, made by its generate.py) into the repo:
// the manifest and sheets for the Angular component, and a generated buddy.css for the pages that
// run no script (desktop shell, Angular boot screen). Run after regenerating the sprites:
//   node desktop/scripts/sync-buddy.mjs [out dir] [manifest] [atlas]
// The defaults are out/, manifest.json and front-atlas.png. An outfit is its own output folder
// (out/office), and a larger clip set its own manifest and atlas pair; the atlas is always copied
// as front-atlas.png. generate.py empties out/ before it writes, so everything is checked before
// anything is replaced.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { atlasSize, buddyCss } from './buddy-css.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const source = path.resolve(process.argv[2] ?? path.join(repo, '..', '..', 'brand', 'buddy-sprites', 'out'));
const MANIFEST = process.argv[3] ?? 'manifest.json';
const SOURCE_ATLAS = process.argv[4] ?? 'front-atlas.png';
const ATLAS = 'front-atlas.png';

// A list is a preference order: a clip the generator adds later (#349) takes over on the next sync.
const MOODS = {
  loading: { loop: ['idle', 'blink', 'idle', ['think', 'look-around']] },
  error: { enter: [['oops', 'head-shake'], ['oops', 'head-shake']], loop: ['idle', 'blink'] },
};

const targets = {
  assets: path.join(repo, 'frontend', 'src', 'assets', 'buddy'),
  manifest: path.join(repo, 'frontend', 'src', 'app', 'shared', 'components', 'buddy', 'buddy-manifest.json'),
  shell: path.join(repo, 'desktop', 'ui', 'buddy'),
};

/** Width and height from a PNG's IHDR chunk. */
function pngSize(file) {
  const head = fs.readFileSync(file).subarray(0, 24);
  return { width: head.readUInt32BE(16), height: head.readUInt32BE(20) };
}

function check(manifest) {
  const sheets = Object.values(manifest).filter((m) => m.atlas_y === undefined).map((m) => m.sheet);
  const missing = [SOURCE_ATLAS, ...sheets].filter((f) => !fs.existsSync(path.join(source, f)));
  if (missing.length) throw new Error(`Missing in ${source}: ${missing.join(', ')}`);
  const want = atlasSize(manifest);
  const have = pngSize(path.join(source, SOURCE_ATLAS));
  if (have.width < want.width || have.height < want.height) {
    throw new Error(`${SOURCE_ATLAS} is ${have.width}x${have.height}, the manifest needs ${want.width}x${want.height}`);
  }
  return sheets;
}

function replaceDir(dir, files) {
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  for (const [name, content] of Object.entries(files)) fs.writeFileSync(path.join(dir, name), content);
}

// generate.py writes CRLF on Windows; the repo keeps LF.
const raw = fs.readFileSync(path.join(source, MANIFEST), 'utf8').replace(/\r\n/g, '\n');
const manifest = JSON.parse(raw);
const sheets = check(manifest);
const css = buddyCss(manifest, { atlas: ATLAS, moods: MOODS });
const read = (f) => fs.readFileSync(path.join(source, f));
const images = { [ATLAS]: read(SOURCE_ATLAS), ...Object.fromEntries(sheets.map((f) => [f, read(f)])) };

replaceDir(targets.assets, { ...images, 'buddy.css': css });
replaceDir(targets.shell, { [ATLAS]: images[ATLAS], 'buddy.css': css });
fs.writeFileSync(targets.manifest, raw);
console.log(`Synced ${Object.keys(manifest).length} clips from ${source}`);
