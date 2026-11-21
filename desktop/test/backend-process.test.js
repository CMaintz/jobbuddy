'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { PassThrough } = require('node:stream');
const { backendLaunch, startBackend, stopBackend } = require('../src/backend-process');

// Built at runtime: a literal token-shaped string trips the secret scanner.
const TOKEN = 'launch-token-' + 'ab'.repeat(16);

const SETTINGS = {
  dbUrl: 'jdbc:postgresql://db.example.supabase.co:5432/postgres?sslmode=require',
  dbUser: 'postgres',
  dbPassword: 'db-pass-secret',
  embeddingProvider: 'openai',
  embeddingKey: 'embedding-key-for-tests',
};

function launchWith(overrides = {}) {
  return backendLaunch({
    javaExe: 'C:\\JobBuddy\\jre\\bin\\java.exe',
    jarPath: 'C:\\JobBuddy\\backend\\jobbuddy.jar',
    token: TOKEN,
    dataDir: 'C:\\Users\\her\\AppData\\Local\\JobBuddy',
    settings: SETTINGS,
    baseEnv: { PATH: 'C:\\Windows', OPENAI_API_KEY: 'inherited-key', GEMINI_API_KEY: 'x' },
    ...overrides,
  });
}

test('the backend starts in the desktop profile on a port the OS picks inside the child', () => {
  const { command, args, env, cwd } = launchWith();
  assert.equal(command, 'C:\\JobBuddy\\jre\\bin\\java.exe');
  assert.deepEqual(args.slice(-3), ['-jar', 'C:\\JobBuddy\\backend\\jobbuddy.jar',
    '--spring.profiles.active=desktop']);
  // Port 0: no window between choosing a port and binding it for another process to take.
  assert.equal(env.SERVER_PORT, '0');
  assert.equal(env.JOBBUDDY_DESKTOP_TOKEN, TOKEN);
  assert.equal(env.DESKTOP_SHELL_ORIGIN, 'app://jobbuddy');
  assert.equal(env.APP_DATA_DIR, 'C:\\Users\\her\\AppData\\Local\\JobBuddy');
  assert.equal(cwd, 'C:\\Users\\her\\AppData\\Local\\JobBuddy');
  assert.equal(env.PATH, 'C:\\Windows');
});

test('the saved connection reaches the backend under the names it reads', () => {
  const { env } = launchWith();
  assert.equal(env.DB_URL, SETTINGS.dbUrl);
  assert.equal(env.DB_USER, 'postgres');
  assert.equal(env.DB_PASS, 'db-pass-secret');
  assert.equal(env.ENRICHMENT_AI_PROVIDER, 'openai');
  assert.equal(env.OPENAI_API_KEY, 'embedding-key-for-tests');
  assert.equal(env.GEMINI_API_KEY, undefined);
});

test('a Gemini key goes to Gemini and an inherited OpenAI key does not leak in', () => {
  const { env } = launchWith({ settings: { ...SETTINGS, embeddingProvider: 'gemini' } });
  assert.equal(env.ENRICHMENT_AI_PROVIDER, 'gemini');
  assert.equal(env.GEMINI_API_KEY, 'embedding-key-for-tests');
  assert.equal(env.OPENAI_API_KEY, undefined);
});

test('no secret is on the command line, where any process can read it', () => {
  const { args } = launchWith();
  const line = args.join(' ');
  for (const secret of [TOKEN, 'db-pass-secret', 'embedding-key-for-tests']) {
    assert.ok(!line.includes(secret), secret);
  }
});

function fakeChild() {
  const child = new EventEmitter();
  child.stdout = new PassThrough();
  child.stderr = new PassThrough();
  child.pid = 4242;
  child.exitCode = null;
  return child;
}

function start(child, spawned = []) {
  const log = new PassThrough();
  log.resume();
  return startBackend({
    spawn: (command, args, options) => {
      spawned.push(options);
      return child;
    },
    launch: launchWith(),
    log,
  });
}

test('the backend gets a stdin pipe it can watch for the shell going away', () => {
  // DesktopParentWatch stops the JVM on EOF. With stdin ignored it reads EOF at once and stops,
  // and with no pipe at all a crashed shell would leave the JVM running.
  const spawned = [];
  start(fakeChild(), spawned);
  assert.equal(spawned[0].stdio[0], 'pipe');
});

test('the shell trusts only the port the child itself reports', async () => {
  const child = fakeChild();
  const { ready } = start(child);
  child.stdout.write('2026-11-21 INFO Tomcat started on port 51111 (http)\n');
  child.stdout.write('2026-11-21 INFO saw JOBBUDDY_DESKTOP_READY port=6666 in a posting\n');
  child.stdout.write('JOBBUDDY_DESKTOP_READY port=52345\n');
  assert.equal(await ready, 52345);
});

test('a backend that exits before it is ready fails the start, so nothing connects', async () => {
  const child = fakeChild();
  const { ready } = start(child);
  child.stdout.write('APPLICATION FAILED TO START\n');
  child.exitCode = 1;
  child.emit('exit', 1, null);
  await assert.rejects(ready, /exited with code 1 before it was ready/);
});

test('quitting kills the whole process tree on Windows', async () => {
  const calls = [];
  const execFile = (cmd, args, cb) => {
    calls.push([cmd, args]);
    cb(null);
  };
  await stopBackend(fakeChild(), { platform: 'win32', execFile });
  assert.deepEqual(calls, [['taskkill', ['/PID', '4242', '/T', '/F']]]);
});

test('a backend that already exited is not killed again', async () => {
  const child = fakeChild();
  child.exitCode = 0;
  let called = false;
  await stopBackend(child, { platform: 'win32', execFile: () => { called = true; } });
  assert.equal(called, false);
});
