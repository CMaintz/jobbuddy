'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { contentSecurityPolicy } = require('../src/csp');
const { registerAppProtocol } = require('../src/app-protocol-handler');

const BACKEND = 'http://127.0.0.1:51234';

function directives(policy) {
  return Object.fromEntries(policy.split(';').map((d) => d.trim()).filter(Boolean)
    .map((d) => { const [name, ...values] = d.split(/\s+/); return [name, values]; }));
}

test('scripts come only from the app itself: no inline, no eval, no remote', () => {
  const d = directives(contentSecurityPolicy(BACKEND));
  assert.deepEqual(d['script-src'], ["'self'"]);
  assert.deepEqual(d['default-src'], ["'self'"]);
  assert.deepEqual(d['object-src'], ["'none'"]);
});

test('the page talks to the backend and nowhere else', () => {
  const d = directives(contentSecurityPolicy(BACKEND));
  assert.deepEqual(d['connect-src'], ["'self'", BACKEND]);
  assert.deepEqual(d['img-src'], ["'self'", BACKEND, 'data:', 'blob:']);
});

test('fonts and styles are local, so nothing is fetched from Google', () => {
  const policy = contentSecurityPolicy(BACKEND);
  assert.ok(!/googleapis|gstatic|https:/.test(policy), policy);
});

function serveFrom(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'jb-ui-'));
  for (const [name, body] of Object.entries(files)) fs.writeFileSync(path.join(root, name), body);
  let handler;
  registerAppProtocol({
    protocol: { handle: (scheme, fn) => { handler = fn; } },
    fetch: async (url) => new Response(fs.readFileSync(new URL(url)), { headers: { 'content-type': 'text/html' } }),
    distRoot: root,
    backendOrigin: () => BACKEND,
  });
  return (url) => handler(new Request(url));
}

test('every page and file the app serves carries the policy', async () => {
  const get = serveFrom({ 'index.html': '<html></html>', 'main.js': 'x' });
  for (const url of ['app://jobbuddy/', 'app://jobbuddy/jobs/feed', 'app://jobbuddy/main.js']) {
    const response = await get(url);
    assert.equal(response.status, 200, url);
    assert.equal(response.headers.get('content-security-policy'), contentSecurityPolicy(BACKEND), url);
  }
});

test('the app page links no stylesheet or font from the internet', () => {
  const index = fs.readFileSync(path.join(__dirname, '..', '..', 'frontend', 'src', 'index.html'), 'utf8');
  assert.ok(!/https?:\/\//.test(index), 'index.html loads something remote');
});
