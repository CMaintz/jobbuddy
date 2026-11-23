// Copies the backend jar and the Angular build into stage/, where the app and electron-builder
// expect them (src/paths.js, electron-builder.yml).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const repo = path.dirname(root);
const stage = path.join(root, 'stage');

const libs = path.join(repo, 'backend', 'build', 'libs');
const jars = fs.existsSync(libs) ? fs.readdirSync(libs).filter((f) => f.endsWith('.jar') && !f.endsWith('-plain.jar')) : [];
if (jars.length !== 1) throw new Error(`Expected one boot jar in ${libs}, found: ${jars.join(', ') || 'none'}`);
fs.mkdirSync(path.join(stage, 'backend'), { recursive: true });
fs.copyFileSync(path.join(libs, jars[0]), path.join(stage, 'backend', 'jobbuddy.jar'));

const ui = path.join(repo, 'frontend', 'dist', 'frontend', 'browser');
if (!fs.existsSync(path.join(ui, 'index.html'))) throw new Error(`No Angular build in ${ui}`);
fs.rmSync(path.join(stage, 'ui'), { recursive: true, force: true });
fs.cpSync(ui, path.join(stage, 'ui'), { recursive: true });
console.log(`Staged ${jars[0]} and the Angular build in ${stage}`);
