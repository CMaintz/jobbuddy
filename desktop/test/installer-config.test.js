'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// The nsis block of electron-builder.yml as flat key: value pairs; enough for these settings.
function nsisSettings() {
  const yml = fs.readFileSync(path.join(__dirname, '..', 'electron-builder.yml'), 'utf8');
  const block = /^nsis:\n((?: {2}.*\n?)*)/m.exec(yml)[1];
  return Object.fromEntries(block.split('\n').map((l) => /^ {2}(\w+):\s*(\S+)/.exec(l)).filter(Boolean)
    .map((m) => [m[1], m[2]]));
}

test('the installer installs for her alone, with no "all users" choice and so no UAC prompt', () => {
  // An assisted installer that is not per-machine still offers "anyone who uses this computer",
  // and picking it asks for administrator rights she may not have.
  const nsis = nsisSettings();
  assert.equal(nsis.oneClick, 'true');
  assert.equal(nsis.perMachine, 'false');
});

test('the installer ships typst.exe where the shell tells the backend to find it', () => {
  // If the two drift apart, the packaged app starts fine and every designed PDF export fails.
  const { resourcePaths } = require('../src/paths');
  const yml = fs.readFileSync(path.join(__dirname, '..', 'electron-builder.yml'), 'utf8');
  const shipped = /- from: stage\/typst\n\s+to: (\S+)/.exec(yml);
  assert.ok(shipped, 'electron-builder.yml has no stage/typst extra resource');
  const resources = path.join('C:', 'JobBuddy', 'resources');
  const original = process.resourcesPath;
  process.resourcesPath = resources;
  try {
    assert.equal(resourcePaths({ isPackaged: true }).typst, path.join(resources, shipped[1], 'typst.exe'));
  } finally {
    process.resourcesPath = original;
  }
});
