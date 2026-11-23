// Puts a pinned Temurin JRE in stage/jre, checked against its published SHA-256. A full JRE
// rather than a jlink image: a module jlink leaves out (TLS for Supabase, Danish locale data)
// would only fail on her PC, and a pinned download does not depend on the builder's JDK.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// renovate: datasource=github-releases depName=adoptium/temurin21-binaries
const RELEASE = 'jdk-21.0.13+9';
const ASSET = 'OpenJDK21U-jre_x64_windows_hotspot_21.0.13_9.zip';
const SHA256 = '2332c0b4cff6bed722da3567c91d5e47568bcba863fa4945d0a8d6fc76158628';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const cacheZip = path.join(root, '.cache', ASSET);
const target = path.join(root, 'stage', 'jre');
const marker = path.join(target, '.release');

function sha256(file) {
  return createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

if (fs.existsSync(marker) && fs.readFileSync(marker, 'utf8') === RELEASE) process.exit(0);

if (!fs.existsSync(cacheZip) || sha256(cacheZip) !== SHA256) {
  const url = `https://github.com/adoptium/temurin21-binaries/releases/download/${encodeURIComponent(RELEASE)}/${ASSET}`;
  console.log(`Downloading ${url}`);
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
  fs.mkdirSync(path.dirname(cacheZip), { recursive: true });
  fs.writeFileSync(cacheZip, Buffer.from(await response.arrayBuffer()));
}
const actual = sha256(cacheZip);
if (actual !== SHA256) throw new Error(`${ASSET} has SHA-256 ${actual}, expected ${SHA256}`);

const unpack = path.join(root, '.cache', 'jre-unpack');
fs.rmSync(unpack, { recursive: true, force: true });
fs.mkdirSync(unpack, { recursive: true });
// Windows' own bsdtar reads zip; Git Bash's GNU tar, first on PATH there, does not.
execFileSync(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'tar.exe'),
  ['-xf', cacheZip, '-C', unpack], { stdio: 'inherit' });
const [top] = fs.readdirSync(unpack);
fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.renameSync(path.join(unpack, top), target);
fs.writeFileSync(marker, RELEASE);
console.log(`JRE ${RELEASE} -> ${target}`);
