'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repo = path.join(__dirname, '..', '..');

function read(relative) {
  return fs.readFileSync(path.join(repo, relative), 'utf8');
}

test('the desktop app ships the Typst version the backend is built and tested with', () => {
  // Renovate bumps backend/mise.toml on its own. If the desktop kept shipping the old compiler,
  // a template using a newer feature would pass CI and fail every export on her PC.
  const desktop = /const VERSION = '([^']+)'/.exec(read('desktop/scripts/fetch-typst.mjs'))[1];
  const backend = /^typst = "([^"]+)"/m.exec(read('backend/mise.toml'))[1];
  const docker = /^ARG TYPST_VERSION=(\S+)/m.exec(read('backend/Dockerfile'))[1];
  assert.equal(desktop, backend);
  assert.equal(docker, backend);
});
