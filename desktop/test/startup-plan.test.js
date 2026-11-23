'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { startupPlan } = require('../src/startup-plan');

test('a ready PC starts straight away, hidden or not', () => {
  for (const hidden of [false, true]) {
    assert.deepEqual(startupPlan({ hasSettings: true, checksOk: true, hidden }), { start: true, warn: false });
  }
});

test('opened by her with a failed check: show what to fix, start nothing', () => {
  assert.deepEqual(startupPlan({ hasSettings: true, checksOk: false, hidden: false }), { start: false, warn: false });
});

test('started at login with a failed check: run anyway and say so from the tray', () => {
  // Nobody sees a hidden window. Without this, a login start with Git missing sat on an unseen
  // setup page with no backend, so no enrichment and no reminders all day.
  assert.deepEqual(startupPlan({ hasSettings: true, checksOk: false, hidden: true }), { start: true, warn: true });
});

test('no connection yet: setup, and a tray message when nobody can see it', () => {
  assert.deepEqual(startupPlan({ hasSettings: false, checksOk: true, hidden: false }), { start: false, warn: false });
  assert.deepEqual(startupPlan({ hasSettings: false, checksOk: true, hidden: true }), { start: false, warn: true });
});
