const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/**
 * Runs extension files the way the browser does: classic scripts sharing one global scope,
 * with only the given globals (chrome, location, ...) in reach. Returns that scope.
 */
function loadScripts(files, globals = {}) {
  const context = vm.createContext({ ...globals });
  for (const file of files) {
    const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
    vm.runInContext(source, context, { filename: file });
  }
  return context;
}

/**
 * Copies a value out of the script scope into this realm. Objects built there carry that
 * realm's Object.prototype, which assert.deepStrictEqual would report as a mismatch.
 */
function plain(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

module.exports = { loadScripts, plain };
