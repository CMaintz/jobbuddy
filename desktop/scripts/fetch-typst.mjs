// Puts the pinned Typst CLI in stage/typst, checked against its published SHA-256. The backend
// renders every designed CV and letter with it, so the version must equal the one the backend
// pins (backend/mise.toml, backend/Dockerfile): templates are Typst source tied to a compiler.
// test/typst-version.test.js fails when the two drift apart.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// renovate: datasource=github-releases depName=typst/typst
const VERSION = '0.15.1';
const ASSET = 'typst-x86_64-pc-windows-msvc.zip';
const SHA256 = '19ce3551153c2fe7ee9fa2f95208310c8f4d3209fedb699e0333faf8913f6736';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const cacheZip = path.join(root, '.cache', `typst-${VERSION}-${ASSET}`);
const target = path.join(root, 'stage', 'typst');
const marker = path.join(target, '.version');

function sha256(file) {
  return createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

const exe = path.join(target, 'typst.exe');
if (fs.existsSync(marker) && fs.readFileSync(marker, 'utf8') === VERSION && fs.existsSync(exe)) process.exit(0);

if (!fs.existsSync(cacheZip) || sha256(cacheZip) !== SHA256) {
  const url = `https://github.com/typst/typst/releases/download/v${VERSION}/${ASSET}`;
  console.log(`Downloading ${url}`);
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  fs.mkdirSync(path.dirname(cacheZip), { recursive: true });
  fs.writeFileSync(cacheZip, Buffer.from(await response.arrayBuffer()));
}
const actual = sha256(cacheZip);
if (actual !== SHA256) throw new Error(`${ASSET} has SHA-256 ${actual}, expected ${SHA256}`);

const unpack = path.join(root, '.cache', 'typst-unpack');
fs.rmSync(unpack, { recursive: true, force: true });
fs.mkdirSync(unpack, { recursive: true });
// Windows' own bsdtar reads zip; Git Bash's GNU tar, first on PATH there, does not.
execFileSync(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'tar.exe'),
  ['-xf', cacheZip, '-C', unpack], { stdio: 'inherit' });
const [top] = fs.readdirSync(unpack);
fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(target, { recursive: true });
fs.copyFileSync(path.join(unpack, top, 'typst.exe'), exe);
// Apache-2.0 asks for the LICENSE and NOTICE to travel with the binary.
for (const file of ['LICENSE', 'NOTICE']) fs.copyFileSync(path.join(unpack, top, file), path.join(target, file));
fs.writeFileSync(marker, VERSION);
console.log(`Typst ${VERSION} -> ${target}`);
