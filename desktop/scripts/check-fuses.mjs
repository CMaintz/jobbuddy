// Fails the package build unless the packaged exe has its debugging and Node entry points fused
// off. Reads the fuse wire from the binary itself, so it checks what ships, not the config.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { getCurrentFuseWire, FuseV1Options } from '@electron/fuses';

const DISABLE = 48;
const ENABLE = 49;
const EXPECTED = {
  RunAsNode: DISABLE,
  EnableNodeOptionsEnvironmentVariable: DISABLE,
  EnableNodeCliInspectArguments: DISABLE,
  OnlyLoadAppFromAsar: ENABLE,
};

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const exe = process.argv[2] ?? path.join(root, 'dist', 'win-unpacked', 'JobBuddy.exe');
const wire = await getCurrentFuseWire(exe);
const wrong = Object.entries(EXPECTED).filter(([name, state]) => wire[FuseV1Options[name]] !== state);
if (wrong.length > 0) {
  console.error(`Fuses not set in ${exe}: ${wrong.map(([name]) => name).join(', ')}`);
  process.exit(1);
}
console.log(`Fuses set in ${exe}: ${Object.keys(EXPECTED).join(', ')}`);
