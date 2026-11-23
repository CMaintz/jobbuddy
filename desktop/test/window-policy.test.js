'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const policy = require('../src/window-policy');

const SHELL = 'file:///C:/Program%20Files/JobBuddy/resources/app.asar/ui/';

test('the window stays on the app and the shell pages', () => {
  for (const url of ['app://jobbuddy/', 'app://jobbuddy/cv/import', `${SHELL}setup.html?edit=1`]) {
    assert.equal(policy.navigationDecision(url, SHELL), 'allow', url);
  }
});

test('nothing else loads in the window, file pages and look-alikes included', () => {
  for (const url of [
    'https://example.com/',
    'http://127.0.0.1:51234/api/v1/auth/me',
    'app://jobbuddy.evil/',
    'file:///C:/Users/her/Downloads/evil.html',
    'file:///C:/Program%20Files/JobBuddy/resources/app.asar/ui-evil/x.html',
    'javascript:alert(1)',
  ]) {
    assert.equal(policy.navigationDecision(url, SHELL), 'deny', url);
  }
});

test('mail and phone links open in her own programs', () => {
  assert.equal(policy.navigationDecision('mailto:hr@firma.dk', SHELL), 'external');
  assert.equal(policy.navigationDecision('tel:+4512345678', SHELL), 'external');
});

test('new windows open web, mail and phone links outside, and nothing else', () => {
  for (const url of ['https://www.jobindex.dk/job/1', 'http://example.com', 'mailto:a@b.dk', 'tel:123']) {
    assert.equal(policy.windowOpenDecision(url), 'external', url);
  }
  for (const url of ['file:///C:/Windows/System32/calc.exe', 'smb://host/share', 'ms-msdt:/id', 'javascript:x', 'app://jobbuddy/']) {
    assert.equal(policy.windowOpenDecision(url), 'deny', url);
  }
});

test('only the shell pages may call the shell (save settings, quit)', () => {
  assert.equal(policy.isShellSender(`${SHELL}setup.html`, SHELL), true);
  for (const url of ['app://jobbuddy/', 'file:///C:/Users/her/evil.html', `${SHELL.slice(0, -1)}-evil/setup.html`, '']) {
    assert.equal(policy.isShellSender(url, SHELL), false, url);
  }
});

test('only the app page learns where the backend is', () => {
  assert.equal(policy.isAppSender('app://jobbuddy/dashboard'), true);
  for (const url of ['app://jobbuddy.evil/', `${SHELL}setup.html`, 'https://example.com/', '']) {
    assert.equal(policy.isAppSender(url), false, url);
  }
});

test('the installed app has no DevTools; the checkout keeps them', () => {
  const base = { preload: 'p.js', icon: 'i.png', startHidden: false };
  assert.equal(policy.windowOptions({ ...base, isPackaged: true }).webPreferences.devTools, false);
  assert.equal(policy.windowOptions({ ...base, isPackaged: false }).webPreferences.devTools, true);
  const prefs = policy.windowOptions({ ...base, isPackaged: true }).webPreferences;
  assert.equal(prefs.contextIsolation, true);
  assert.equal(prefs.sandbox, true);
  assert.equal(prefs.nodeIntegration, false);
});

test('the installed app has no menu, so no DevTools shortcut either', () => {
  const calls = [];
  const Menu = { setApplicationMenu: (m) => calls.push(m) };
  policy.applyMenuPolicy(Menu, true);
  assert.deepEqual(calls, [null]);
  policy.applyMenuPolicy(Menu, false);
  assert.deepEqual(calls, [null]);
});

test('no page gets camera, microphone, location or the like', () => {
  for (const permission of ['media', 'geolocation', 'notifications', 'clipboard-read', 'midi', 'openExternal', 'fullscreen']) {
    assert.equal(policy.permissionAllowed(permission, 'app://jobbuddy/'), false, permission);
  }
});

test('the app page may copy to the clipboard, for its copy buttons; nothing else may', () => {
  assert.equal(policy.permissionAllowed('clipboard-sanitized-write', 'app://jobbuddy/output'), true);
  // Electron's permission check passes an origin, without the trailing slash.
  assert.equal(policy.permissionAllowed('clipboard-sanitized-write', 'app://jobbuddy'), true);
  assert.equal(policy.permissionAllowed('clipboard-sanitized-write', 'app://jobbuddy.evil'), false);
  assert.equal(policy.permissionAllowed('clipboard-sanitized-write', `${SHELL}setup.html`), false);
  assert.equal(policy.permissionAllowed('clipboard-sanitized-write', 'https://example.com/'), false);
});
