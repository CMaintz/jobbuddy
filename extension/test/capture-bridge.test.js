const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { loadScripts } = require('./load-scripts');

// Written as a literal on purpose: the frontend's apply screen reads this key, and its spec
// (apply.component.spec.ts) pins the same string. The two cannot share a constant without a
// build step for the extension, so each side's test fails if only one side is renamed.
const CAPTURE_KEY = 'jb-captured-job';
const CAPTURE = { title: 'Backend-udvikler', company: 'Netcompany', savedAt: 1 };

/** Loads the bridge on a page at `origin`, with `appUrl` saved in the popup's settings. */
function openPage({ origin, appUrl }) {
  const pageStorage = new Map();
  const events = [];
  let parked = CAPTURE;
  const chrome = {
    storage: {
      sync: { get: (key, callback) => callback(appUrl === undefined ? {} : { [key]: appUrl }) },
      local: {
        get: (key, callback) => callback({ [key]: parked }),
        remove: () => { parked = undefined; },
      },
    },
  };
  loadScripts(['app-origin.js', 'capture-bridge.js'], {
    chrome,
    URL,
    location: { origin },
    localStorage: { setItem: (key, value) => pageStorage.set(key, value) },
    window: { dispatchEvent: (event) => events.push(event.type) },
    CustomEvent: class { constructor(type) { this.type = type; } },
  });
  return { pageStorage, events, parked: () => parked };
}

describe('capture bridge', () => {
  it('hands the parked capture to the configured app under the shared key', () => {
    const page = openPage({ origin: 'https://jobbuddy.example.dk', appUrl: 'https://jobbuddy.example.dk/' });

    assert.deepEqual([...page.pageStorage.keys()], [CAPTURE_KEY]);
    assert.deepEqual(JSON.parse(page.pageStorage.get(CAPTURE_KEY)), CAPTURE);
    assert.deepEqual(page.events, ['jb-capture-ready']);
    assert.equal(page.parked(), undefined);
  });

  it('delivers to the default app origin when no URL has been saved', () => {
    const page = openPage({ origin: 'http://localhost:4200', appUrl: undefined });

    assert.ok(page.pageStorage.has(CAPTURE_KEY));
  });

  it('leaves the capture parked on a page that is not the configured app', () => {
    const page = openPage({ origin: 'http://localhost', appUrl: 'http://localhost:4200' });

    assert.equal(page.pageStorage.size, 0);
    assert.deepEqual(page.events, []);
    assert.deepEqual(page.parked(), CAPTURE);
  });
});
