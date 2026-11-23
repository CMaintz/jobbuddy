'use strict';

const { execFile } = require('node:child_process');

// Where Windows keeps PATH, machine part first, as Explorer composes it for a new process.
const REGISTRY_KEYS = {
  machine: 'HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment',
  user: 'HKCU\\Environment',
};
const VALUE_LINE = /^\s*Path\s+REG_(?:EXPAND_)?SZ\s+(.*)$/i;

/** The Path value from `reg query <key> /v Path` output, or null when there is none. */
function parseRegQuery(output) {
  for (const line of String(output).split(/\r?\n/)) {
    const match = VALUE_LINE.exec(line);
    if (match) return match[1].trim();
  }
  return null;
}

function envKey(env, name) {
  return Object.keys(env).find((k) => k.toUpperCase() === name);
}

// REG_EXPAND_SZ entries such as %USERPROFILE%\.local\bin, expanded the way Windows does: a
// variable that is not set stays as written.
function expand(value, env) {
  return value.replace(/%([^%]+)%/g, (whole, name) => {
    const key = envKey(env, name.toUpperCase());
    return key ? env[key] : whole;
  });
}

/** Reads one scope's Path through reg.exe; null when there is none. */
function readRegistryPath(scope) {
  return new Promise((resolve, reject) => {
    execFile('reg', ['query', REGISTRY_KEYS[scope], '/v', 'Path'], { windowsHide: true },
      (error, stdout) => (error ? reject(error) : resolve(parseRegQuery(stdout))));
  });
}

async function readOrNull(read, scope) {
  try {
    return await read(scope);
  } catch {
    return null;
  }
}

/**
 * A copy of `env` with PATH as it is in the registry now. Windows never updates PATH in a running
 * process, and JobBuddy runs for days in the tray: Git or Claude Code installed meanwhile is only
 * in the registry. Without this, "Tjek igen" and a restarted backend kept failing until "Afslut".
 *
 * @param {object} deps
 * @param {NodeJS.ProcessEnv} deps.env the shell's own environment; not modified
 * @param {(scope: 'machine' | 'user') => Promise<string | null>} [deps.readRegistryPath]
 * @returns {Promise<NodeJS.ProcessEnv>} with exactly one PATH key; the old PATH when the
 *   registry cannot be read
 */
async function freshEnv({ env, readRegistryPath: read = readRegistryPath }) {
  const parts = [await readOrNull(read, 'machine'), await readOrNull(read, 'user')]
    .filter((part) => part)
    .map((part) => expand(part, env));
  if (parts.length === 0) return { ...env };
  const fresh = { ...env };
  const key = envKey(fresh, 'PATH') ?? 'Path';
  for (const k of Object.keys(fresh)) if (k.toUpperCase() === 'PATH') delete fresh[k];
  fresh[key] = parts.join(';').split(';').filter(Boolean).join(';');
  return fresh;
}

module.exports = { parseRegQuery, freshEnv, readRegistryPath };
