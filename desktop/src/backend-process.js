'use strict';

const readline = require('node:readline');

/** The page origin the backend's CORS admits; app-protocol.js serves the UI from it. */
const SHELL_ORIGIN = 'app://jobbuddy';

// Printed by DesktopReadyAnnouncer on a line of its own. Anchored, so a log line that merely
// quotes it (say, in crawled posting text) cannot pass as the announcement.
const READY_LINE = /^JOBBUDDY_DESKTOP_READY port=(\d{1,5})$/;

const EMBEDDING_KEY_VARS = { openai: 'OPENAI_API_KEY', gemini: 'GEMINI_API_KEY' };

/**
 * How to start the backend. Secrets go in the environment only: a command line is readable by
 * every process on the PC.
 *
 * @param {object} p
 * @param {string} p.javaExe the bundled JRE's java.exe
 * @param {string} p.jarPath the Spring Boot jar
 * @param {string} p.token this launch's JOBBUDDY_DESKTOP_TOKEN
 * @param {string} p.dataDir APP_DATA_DIR, also the working directory
 * @param {import('./settings-store').Settings} p.settings the saved connection
 * @param {NodeJS.ProcessEnv} p.baseEnv the shell's own environment
 */
function backendLaunch({ javaExe, jarPath, token, dataDir, settings, baseEnv }) {
  const env = { ...baseEnv };
  // The user's own shell may carry API keys for other tools; only the one she gave JobBuddy
  // decides which provider embeds.
  for (const name of Object.values(EMBEDDING_KEY_VARS)) delete env[name];
  Object.assign(env, {
    // The OS picks a free port inside the child as it binds, so no other process can take it
    // between choosing and binding. The child reports the port it got (see startBackend).
    SERVER_PORT: '0',
    JOBBUDDY_DESKTOP_TOKEN: token,
    DESKTOP_SHELL_ORIGIN: SHELL_ORIGIN,
    APP_DATA_DIR: dataDir,
    DB_URL: settings.dbUrl,
    DB_USER: settings.dbUser,
    DB_PASS: settings.dbPassword,
    ENRICHMENT_AI_PROVIDER: settings.embeddingProvider,
    [EMBEDDING_KEY_VARS[settings.embeddingProvider]]: settings.embeddingKey,
  });
  return {
    command: javaExe,
    // Exclusive bind is the JDK's Windows default; stated here because the shell relies on it:
    // without it another process could bind the same port alongside the backend.
    args: ['-Dsun.net.useExclusiveBind=true', '-jar', jarPath, '--spring.profiles.active=desktop'],
    env,
    cwd: dataDir,
  };
}

/**
 * Starts the backend and resolves `ready` with the port it bound, read from its own stdout.
 * Nothing else can write to that pipe, so a port from it is the backend's and no impostor's.
 * Rejects if the child exits first, a failed bind included, so the shell never connects to a
 * port it did not get from the child.
 *
 * @param {object} p
 * @param {typeof import('node:child_process').spawn} p.spawn
 * @param {ReturnType<typeof backendLaunch>} p.launch
 * @param {NodeJS.WritableStream} p.log receives everything the child prints
 */
function startBackend({ spawn, launch, log }) {
  const child = spawn(launch.command, launch.args, {
    env: launch.env,
    cwd: launch.cwd,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  child.stdout.pipe(log, { end: false });
  child.stderr.pipe(log, { end: false });
  const ready = new Promise((resolve, reject) => {
    readline.createInterface({ input: child.stdout }).on('line', (line) => {
      const match = READY_LINE.exec(line.trim());
      if (match) resolve(Number(match[1]));
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      reject(new Error(`The backend exited with code ${code ?? signal} before it was ready`));
    });
  });
  return { child, ready };
}

/**
 * Stops the backend and everything it started (the Claude CLI it may be running). On Windows a
 * plain kill ends java.exe only and leaves its children running, hence taskkill /T.
 *
 * @param {import('node:child_process').ChildProcess} child
 * @param {{ platform?: string, execFile?: Function }} [deps]
 */
function stopBackend(child, { platform = process.platform, execFile } = {}) {
  if (!child || child.exitCode !== null || child.signalCode) return Promise.resolve();
  if (platform !== 'win32') {
    child.kill('SIGTERM');
    return Promise.resolve();
  }
  const run = execFile ?? require('node:child_process').execFile;
  return new Promise((resolve) => {
    // A failure here means the process is already gone; quitting goes ahead either way.
    run('taskkill', ['/PID', String(child.pid), '/T', '/F'], () => resolve());
  });
}

module.exports = { SHELL_ORIGIN, backendLaunch, startBackend, stopBackend };
