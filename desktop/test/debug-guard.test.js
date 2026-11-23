'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { refuseRemoteDebugging } = require('../src/debug-guard');

function appWith({ isPackaged, switches }) {
  const exits = [];
  return {
    exits,
    app: {
      isPackaged,
      commandLine: { hasSwitch: (name) => switches.includes(name) },
      exit: (code) => exits.push(code),
    },
  };
}

test('the installed app refuses to run with a remote debugging port or pipe', () => {
  // Anything that can start JobBuddy with these could drive the window, and through it send
  // requests that the shell signs with her token.
  for (const flag of ['remote-debugging-port', 'remote-debugging-pipe']) {
    const { app, exits } = appWith({ isPackaged: true, switches: [flag] });
    assert.equal(refuseRemoteDebugging(app), true, flag);
    assert.deepEqual(exits, [1], flag);
  }
});

test('a build run from the checkout can still be debugged', () => {
  const { app, exits } = appWith({ isPackaged: false, switches: ['remote-debugging-port'] });
  assert.equal(refuseRemoteDebugging(app), false);
  assert.deepEqual(exits, []);
});

test('a normal start of the installed app goes ahead', () => {
  const { app, exits } = appWith({ isPackaged: true, switches: [] });
  assert.equal(refuseRemoteDebugging(app), false);
  assert.deepEqual(exits, []);
});
