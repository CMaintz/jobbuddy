'use strict';

const path = require('node:path');

const CLAUDE_INSTALL_FIX =
  'Åbn PowerShell, og kør: irm https://claude.ai/install.ps1 | iex. Genstart derefter JobBuddy. ' +
  'Vejledning: https://code.claude.com/docs/en/setup';
const CLAUDE_LOGIN_FIX =
  'Åbn PowerShell, skriv claude, og tryk Enter. Følg vejledningen, og log ind med din ' +
  'Claude-konto. Kom derefter tilbage, og klik på "Tjek igen".';
const GIT_FIX =
  'Claude Code på Windows skal bruge Git for Windows. Hent det på ' +
  'https://git-scm.com/downloads/win, installer det med standardvalgene, og klik på "Tjek igen".';

/** Case-insensitive, as Windows itself is: process.env copies spell it "Path". */
function envVar(env, name) {
  const key = Object.keys(env).find((k) => k.toUpperCase() === name);
  return key ? env[key] : undefined;
}

/**
 * Finds a command the way cmd.exe would, over PATH x PATHEXT. A shell-less spawn of "claude"
 * finds only claude.exe, but an npm install of Claude Code gives claude.cmd.
 *
 * @param {string} name
 * @param {NodeJS.ProcessEnv} env
 * @param {(file: string) => boolean} exists
 * @returns {string | null}
 */
function resolveOnPath(name, env, exists) {
  const dirs = (envVar(env, 'PATH') ?? '').split(';').filter(Boolean);
  const exts = (envVar(env, 'PATHEXT') ?? '.EXE;.CMD').split(';').filter(Boolean);
  for (const dir of dirs) {
    for (const ext of exts) {
      const file = path.win32.join(dir, name + ext);
      if (exists(file)) return file;
    }
  }
  return null;
}

async function tryRun(run, file, args) {
  try {
    return await run(file, args);
  } catch {
    return { code: -1, stdout: '' };
  }
}

const ok = (id, title) => ({ id, ok: true, title });
const fail = (id, title, fix) => ({ id, ok: false, title, fix });

async function checkClaude(claude, run) {
  if (!claude) return fail('claude', 'Claude Code er ikke installeret', CLAUDE_INSTALL_FIX);
  const version = await tryRun(run, claude, ['--version']);
  if (version.code !== 0) {
    return fail('claude', 'Claude Code er installeret, men kunne ikke starte', CLAUDE_INSTALL_FIX);
  }
  return ok('claude', `Claude Code ${version.stdout.trim().split(/\s/)[0]} er installeret`);
}

function loggedIn(stdout) {
  try {
    const status = JSON.parse(stdout);
    return typeof status.loggedIn === 'boolean' ? status.loggedIn : null;
  } catch {
    return null;
  }
}

// Reads `loggedIn` and ignores the exit code: what the CLI exits with when logged out has not been
// pinned down, and the JSON says it either way.
async function checkLogin(claude, claudeWorks, run) {
  if (!claudeWorks) {
    return fail('login', 'Login kan ikke tjekkes, fordi Claude Code mangler',
      'Løs punktet om Claude Code først.');
  }
  const status = await tryRun(run, claude, ['auth', 'status', '--json']);
  const state = loggedIn(status.stdout);
  if (state === true) return ok('login', 'Du er logget ind i Claude Code');
  if (state === false) return fail('login', 'Du er ikke logget ind i Claude Code', CLAUDE_LOGIN_FIX);
  return fail('login', 'Kunne ikke tjekke, om du er logget ind i Claude Code', CLAUDE_LOGIN_FIX);
}

// "git version 2.51.0.windows.1": a git from WSL, Cygwin or MSYS2 lacks ".windows." and is not
// the Git Bash that Claude Code on Windows runs its commands in.
async function checkGit(git, run) {
  if (!git) return fail('git', 'Git for Windows mangler', GIT_FIX);
  const version = await tryRun(run, git, ['--version']);
  if (version.code !== 0 || !version.stdout.includes('.windows.')) {
    return fail('git', 'Git for Windows mangler', GIT_FIX);
  }
  return ok('git', 'Git for Windows er installeret');
}

/**
 * What must be on her PC before generation can work, each with a plain Danish fix.
 *
 * @param {object} deps
 * @param {NodeJS.ProcessEnv} deps.env
 * @param {(file: string) => boolean} deps.exists
 * @param {(file: string, args: string[]) => Promise<{ code: number, stdout: string }>} deps.run
 * @returns {Promise<Array<{ id: string, ok: boolean, title: string, fix?: string }>>}
 */
async function runChecks({ env, exists, run }) {
  const claudePath = resolveOnPath('claude', env, exists);
  const claude = await checkClaude(claudePath, run);
  const login = await checkLogin(claudePath, claude.ok, run);
  const git = await checkGit(resolveOnPath('git', env, exists), run);
  return [claude, login, git];
}

module.exports = { resolveOnPath, runChecks };
