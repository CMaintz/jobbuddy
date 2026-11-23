'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');

const builderAppId = () => {
  const yml = fs.readFileSync(path.join(__dirname, '..', 'electron-builder.yml'), 'utf8');
  return /^appId:\s*(\S+)\s*$/m.exec(yml)[1];
};

// Loads the real main.js against a stand-in for Electron in which "ready" never comes, so no
// window, tray or notification can exist yet. Whatever main.js did by then, it did first.
function loadMainBeforeReady() {
  const calls = [];
  const fakeElectron = {
    app: {
      isPackaged: false,
      commandLine: { hasSwitch: () => false },
      setAppUserModelId: (id) => calls.push(['setAppUserModelId', id]),
      requestSingleInstanceLock: () => true,
      on: () => {},
      whenReady: () => new Promise(() => {}),
      quit: () => {},
      exit: () => {},
    },
    protocol: { registerSchemesAsPrivileged: () => {} },
    Notification: function Notification() {
      calls.push(['Notification']);
    },
  };
  const mainPath = require.resolve('../src/main');
  const load = Module._load;
  Module._load = function patched(request, ...rest) {
    return request === 'electron' ? fakeElectron : load.call(this, request, ...rest);
  };
  try {
    delete require.cache[mainPath];
    require(mainPath);
  } finally {
    Module._load = load;
    delete require.cache[mainPath];
  }
  return calls;
}

test('Windows knows the app by its installer id before any notification can be shown', () => {
  // Toasts from an installed app are only shown when this id matches the AppUserModelID on the
  // Start-menu shortcut the installer made, which electron-builder sets from appId.
  const calls = loadMainBeforeReady();
  assert.deepEqual(calls, [['setAppUserModelId', builderAppId()]]);
});
