'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { resolveOnPath, runChecks } = require('../src/first-run-checks');

const NPM_DIR = 'C:\\Users\\her\\AppData\\Roaming\\npm';
const GIT_DIR = 'C:\\Program Files\\Git\\cmd';
// Windows spells it "Path"; a copied env object is case-sensitive.
const ENV = { Path: `C:\\Windows;${NPM_DIR};${GIT_DIR}`, PATHEXT: '.COM;.EXE;.BAT;.CMD' };

function existsOnly(...files) {
  const set = new Set(files.map((f) => f.toLowerCase()));
  return (file) => set.has(file.toLowerCase());
}

test('an npm-installed claude.cmd is found through PATHEXT', () => {
  const exists = existsOnly(path.win32.join(NPM_DIR, 'claude.CMD'));
  assert.equal(resolveOnPath('claude', ENV, exists), path.win32.join(NPM_DIR, 'claude.CMD'));
});

const CLAUDE = path.win32.join(NPM_DIR, 'claude.CMD');
const GIT = path.win32.join(GIT_DIR, 'git.EXE');

function runner(responses) {
  return async (file, args) => {
    const key = `${path.win32.basename(file).toLowerCase()} ${args.join(' ')}`;
    if (!(key in responses)) throw new Error(`unexpected command: ${key}`);
    const r = responses[key];
    if (r instanceof Error) throw r;
    return r;
  };
}

const HEALTHY = {
  'claude.cmd --version': { code: 0, stdout: '2.1.330 (Claude Code)\n' },
  'claude.cmd auth status --json': { code: 0, stdout: '{"loggedIn": true, "authMethod": "claude.ai"}' },
  'git.exe --version': { code: 0, stdout: 'git version 2.51.0.windows.1\n' },
};

function check(responses, exists = existsOnly(CLAUDE, GIT)) {
  return runChecks({ env: ENV, exists, run: runner(responses) });
}

test('a ready PC passes all three checks', async () => {
  const results = await check(HEALTHY);
  assert.deepEqual(results.map((r) => [r.id, r.ok]), [['claude', true], ['login', true], ['git', true]]);
});

test('without claude she is told to install it, not that she is logged out', async () => {
  const results = await check(HEALTHY, existsOnly(GIT));
  const [claude, login] = results;
  assert.equal(claude.ok, false);
  assert.match(claude.fix, /install\.ps1/);
  assert.equal(login.ok, false);
  assert.match(login.title, /Claude Code mangler/);
});

test('a claude that will not run is reported as broken', async () => {
  const results = await check({ ...HEALTHY, 'claude.cmd --version': { code: 1, stdout: '' } });
  assert.equal(results[0].ok, false);
  assert.match(results[0].title, /kunne ikke starte/);
});

test('logged out is read from the status, whatever the exit code', async () => {
  const results = await check({
    ...HEALTHY,
    'claude.cmd auth status --json': { code: 1, stdout: '{"loggedIn": false}' },
  });
  assert.equal(results[1].ok, false);
  assert.match(results[1].title, /ikke logget ind/);
  assert.match(results[1].fix, /claude/);
});

test('status output it cannot read is "could not check", not "logged out"', async () => {
  const results = await check({
    ...HEALTHY,
    'claude.cmd auth status --json': { code: 0, stdout: 'Logged in as someone' },
  });
  assert.equal(results[1].ok, false);
  assert.match(results[1].title, /Kunne ikke tjekke/);
});

test('a git that is not Git for Windows does not count', async () => {
  const results = await check({ ...HEALTHY, 'git.exe --version': { code: 0, stdout: 'git version 2.43.0\n' } });
  assert.equal(results[2].ok, false);
  assert.match(results[2].fix, /git-scm\.com/);
});

test('no git at all is reported with where to get it', async () => {
  const results = await check(HEALTHY, existsOnly(CLAUDE));
  assert.equal(results[2].ok, false);
  assert.match(results[2].title, /Git for Windows mangler/);
});

test('a command that fails to spawn is a failed check, not a crash', async () => {
  const results = await check({ ...HEALTHY, 'git.exe --version': new Error('EPERM') });
  assert.equal(results[2].ok, false);
});
