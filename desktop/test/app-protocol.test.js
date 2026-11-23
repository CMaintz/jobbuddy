'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { routeAppRequest } = require('../src/app-protocol');

const DIST = path.resolve('C:/Program Files/JobBuddy/resources/ui');
const has = (...files) => (f) => files.map((x) => path.join(DIST, x)).includes(f);
const exists = has('index.html', 'main-ABC.js', 'assets/i18n/da.json');

test('built files are served from the UI folder', () => {
  assert.deepEqual(routeAppRequest('app://jobbuddy/main-ABC.js', DIST, exists),
    { kind: 'file', file: path.join(DIST, 'main-ABC.js') });
  assert.deepEqual(routeAppRequest('app://jobbuddy/assets/i18n/da.json', DIST, exists),
    { kind: 'file', file: path.join(DIST, 'assets/i18n/da.json') });
});

test('an app route, on reload or deep link, gets the app itself', () => {
  for (const url of ['app://jobbuddy/', 'app://jobbuddy/jobs/feed', 'app://jobbuddy/dashboard?x=1']) {
    assert.deepEqual(routeAppRequest(url, DIST, exists),
      { kind: 'file', file: path.join(DIST, 'index.html') }, url);
  }
});

test('uploads go to the backend, where the token is added', () => {
  assert.deepEqual(routeAppRequest('app://jobbuddy/uploads/profile-photos/me.png?v=2', DIST, exists),
    { kind: 'backend', path: '/uploads/profile-photos/me.png?v=2' });
});

test('nothing outside the UI folder can be read', () => {
  for (const url of [
    'app://jobbuddy/../../Users/her/.claude/.credentials.json',
    'app://jobbuddy/%2e%2e/%2e%2e/Windows/win.ini',
    'app://jobbuddy/..%5C..%5CWindows%5Cwin.ini',
    'app://jobbuddy/C:%5CWindows%5Cwin.ini',
  ]) {
    const route = routeAppRequest(url, DIST, () => true);
    assert.ok(route.kind !== 'file' || route.file.startsWith(DIST + path.sep), url);
  }
});

test('only the app host is served', () => {
  assert.deepEqual(routeAppRequest('app://other/index.html', DIST, exists), { kind: 'notFound' });
});
