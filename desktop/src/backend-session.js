'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { generateToken } = require('./token');
const { TOKEN_HEADER, installTokenHeader } = require('./token-header');
const { backendLaunch, startBackend, stopBackend } = require('./backend-process');

// Flyway against a remote Supabase on a slow line can take a while on first start.
const READY_TIMEOUT_MS = 180_000;

// The backend's console output, every launch appended, on a PC that runs for months.
const LOG_ROLL_BYTES = 10 * 1024 * 1024;

function rollIfLarge(logPath) {
  try {
    if (fs.statSync(logPath).size > LOG_ROLL_BYTES) fs.renameSync(logPath, `${logPath}.old`);
  } catch {
    // No log yet.
  }
}

function withTimeout(promise, ms, message) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * One running backend at a time: start it, know where it is, stop it. Calls `onCrash` when it
 * exits without being asked to.
 *
 * @param {object} deps
 * @param {Electron.Session} deps.session the window's session, for the token header
 * @param {typeof fetch} deps.fetch Electron's net.fetch
 * @param {ReturnType<import('./paths').resourcePaths>} deps.paths
 * @param {string} deps.dataDir
 * @param {() => Promise<NodeJS.ProcessEnv>} deps.environment read at each start, so a program
 *   installed while JobBuddy ran is on the backend's PATH (fresh-env.js)
 * @param {(error: Error) => void} deps.onCrash
 */
function createBackendSession({ session, fetch, paths, dataDir, environment, onCrash }) {
  const logPath = path.join(dataDir, 'logs', 'desktop-backend.log');
  let current = null;

  async function start(settings) {
    await stop();
    fs.mkdirSync(path.dirname(logPath), { recursive: true });
    rollIfLarge(logPath);
    const log = fs.createWriteStream(logPath, { flags: 'a' });
    log.write(`\n=== JobBuddy started ${new Date().toISOString()} ===\n`);

    const token = generateToken();
    const launch = backendLaunch({
      javaExe: paths.java, jarPath: paths.jar, token, dataDir, settings, baseEnv: await environment(),
    });
    const { child, ready } = startBackend({ spawn, launch, log });
    const run = { child, token, origin: null, stopping: false };
    current = run;
    child.once('exit', (code) => {
      log.end(`=== backend exited with ${code} ===\n`);
      const wasReady = run.origin !== null;
      // The freed port could be bound by anything now: stop sending the token there, and stop
      // telling pages and the reminder poll that the backend lives at it.
      if (current === run) {
        current = null;
        session.webRequest.onBeforeSendHeaders(null);
      }
      if (!run.stopping && wasReady) onCrash(new Error(`exit code ${code}`));
    });

    try {
      const port = await withTimeout(ready, READY_TIMEOUT_MS, 'The backend did not start in time');
      // 127.0.0.1, never "localhost": the backend binds IPv4 loopback only, and localhost may
      // resolve to ::1 first.
      run.origin = `http://127.0.0.1:${port}`;
      installTokenHeader(session.webRequest, run.origin, token);
      await checkHealth(run);
      return run.origin;
    } catch (error) {
      run.stopping = true;
      await stopBackend(child);
      throw error;
    }
  }

  async function checkHealth(run) {
    const response = await fetch(`${run.origin}/actuator/health`, { headers: { [TOKEN_HEADER]: run.token } });
    if (!response.ok) throw new Error(`Health check answered ${response.status}`);
  }

  /** GET with the token, for the shell's own calls (reminders) outside the window. */
  async function getJson(apiPath) {
    if (!current?.origin) throw new Error('The backend is not running');
    const response = await fetch(`${current.origin}${apiPath}`, { headers: { [TOKEN_HEADER]: current.token } });
    if (!response.ok) throw new Error(`${apiPath} answered ${response.status}`);
    return response.json();
  }

  async function stop() {
    if (!current) return;
    current.stopping = true;
    const { child } = current;
    current = null;
    await stopBackend(child);
  }

  return {
    start,
    stop,
    getJson,
    logPath,
    origin: () => current?.origin ?? null,
  };
}

module.exports = { createBackendSession };
