'use strict';

const { execFile } = require('node:child_process');

const CHECK_TIMEOUT_MS = 20_000;

/**
 * Runs a first-run check command and reports its exit code and stdout; never rejects.
 *
 * An npm install of Claude Code is claude.cmd, and Node refuses to spawn a batch file without a
 * shell (CVE-2024-27980), so those go through cmd.exe. The arguments are the checks' own
 * constants, never user input, so nothing can be injected into that command line.
 *
 * @param {string} file absolute path from resolveOnPath
 * @param {string[]} args
 * @returns {Promise<{ code: number, stdout: string }>}
 */
function runCommand(file, args) {
  const options = { windowsHide: true, timeout: CHECK_TIMEOUT_MS };
  const done = (resolve) => (err, stdout) => {
    resolve({ code: err ? (typeof err.code === 'number' ? err.code : -1) : 0, stdout: String(stdout ?? '') });
  };
  return new Promise((resolve) => {
    if (/\.(cmd|bat)$/i.test(file)) {
      // /s strips the outer quotes and keeps the inner ones, so a path with spaces survives.
      const line = `""${file}" ${args.join(' ')}"`;
      execFile(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', line],
        { ...options, windowsVerbatimArguments: true }, done(resolve));
    } else {
      execFile(file, args, options, done(resolve));
    }
  });
}

module.exports = { runCommand };
