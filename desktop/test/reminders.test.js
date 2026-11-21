'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createReminderNotifier } = require('../src/reminders');

const REMINDER = {
  type: 'REMINDER_DUE', reminderId: 'r1', companyName: 'Novo Nordisk', jobTitle: 'Kommunikationskonsulent',
  note: 'Ring til Mette', deadline: '2026-11-21',
};
const OUTREACH = {
  type: 'OUTREACH_FOLLOW_UP', outreachId: 'o1', companyName: 'Ørsted', note: 'Lars Hansen',
  deadline: '2026-11-21',
};
const DEADLINE = { type: 'DEADLINE_SOON', applicationId: 'a1', companyName: 'Lego', deadline: '2026-11-25' };
const FOLLOW_UP = { type: 'FOLLOW_UP', applicationId: 'a2', companyName: 'Maersk', daysSinceApplied: 15 };

function setup(nudges, { day = '2026-11-21', seen = [] } = {}) {
  const shown = [];
  const state = { day, saved: seen };
  const notifier = createReminderNotifier({
    fetchNudges: async () => (nudges instanceof Error ? Promise.reject(nudges) : nudges),
    notify: (n) => shown.push(n),
    seen: { load: () => state.saved, save: (keys) => { state.saved = keys; } },
    today: () => state.day,
  });
  return { notifier, shown, state };
}

test('only what she set a date for pops up, not the in-app nudges', async () => {
  const { notifier, shown } = setup([REMINDER, OUTREACH, DEADLINE, FOLLOW_UP]);
  await notifier.poll();
  assert.equal(shown.length, 2);
  assert.match(shown[0].title, /Novo Nordisk/);
  assert.match(shown[0].body, /Ring til Mette/);
  assert.match(shown[1].title, /Ørsted/);
});

test('each reminder pops up once a day, across polls and restarts', async () => {
  const first = setup([REMINDER]);
  await first.notifier.poll();
  await first.notifier.poll();
  assert.equal(first.shown.length, 1);

  const restarted = setup([REMINDER], { seen: first.state.saved });
  await restarted.notifier.poll();
  assert.equal(restarted.shown.length, 0);
});

test('a reminder still open the next day pops up again', async () => {
  const { notifier, shown, state } = setup([REMINDER]);
  await notifier.poll();
  state.day = '2026-11-22';
  await notifier.poll();
  assert.equal(shown.length, 2);
});

test('a failed poll shows nothing and forgets nothing', async () => {
  const { notifier, shown, state } = setup(new Error('backend restarting'), { seen: ['k'] });
  await notifier.poll();
  assert.equal(shown.length, 0);
  assert.deepEqual(state.saved, ['k']);
});
