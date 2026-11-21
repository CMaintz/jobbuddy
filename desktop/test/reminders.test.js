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
const INTERVIEW = {
  type: 'INTERVIEW_SOON', applicationId: 'a3', companyName: 'Coloplast', jobTitle: 'Kommunikationsrådgiver',
  deadline: '2026-11-21', time: '10:30:00',
};

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

test('an interview today pops up with its company and time', async () => {
  const { notifier, shown } = setup([INTERVIEW]);
  await notifier.poll();
  assert.equal(shown.length, 1);
  assert.equal(shown[0].title, 'Jobsamtale i dag hos Coloplast');
  assert.match(shown[0].body, /kl\. 10:30(?!:)/);
  assert.match(shown[0].body, /Kommunikationsrådgiver/);
});

test('an interview tomorrow says tomorrow, and says today on the day', async () => {
  const { notifier, shown, state } = setup([{ ...INTERVIEW, deadline: '2026-11-22' }]);
  await notifier.poll();
  state.day = '2026-11-22';
  await notifier.poll();
  assert.deepEqual(shown.map((n) => n.title),
    ['Jobsamtale i morgen hos Coloplast', 'Jobsamtale i dag hos Coloplast']);
});

test('two interviews the same day both pop up, each only once', async () => {
  const other = { ...INTERVIEW, applicationId: 'a4', companyName: 'Danske Bank', time: '14:00' };
  const first = setup([INTERVIEW, other]);
  await first.notifier.poll();
  await first.notifier.poll();
  assert.deepEqual(first.shown.map((n) => n.title),
    ['Jobsamtale i dag hos Coloplast', 'Jobsamtale i dag hos Danske Bank']);

  const restarted = setup([INTERVIEW, other], { seen: first.state.saved });
  await restarted.notifier.poll();
  assert.equal(restarted.shown.length, 0);
});

test('an interview without a time or company still pops up, without a made-up time', async () => {
  const { notifier, shown } = setup([{ ...INTERVIEW, companyName: null, time: null }]);
  await notifier.poll();
  assert.equal(shown[0].title, 'Jobsamtale i dag hos en virksomhed');
  assert.doesNotMatch(shown[0].body, /kl\./);
});

test("yesterday's interview, still served just after midnight, does not pop up as tomorrow", async () => {
  // The backend clock is UTC, so for an hour or two after Danish midnight it still counts yesterday.
  const { notifier, shown } = setup([{ ...INTERVIEW, deadline: '2026-11-20' }]);
  await notifier.poll();
  assert.equal(shown.length, 0);
});
