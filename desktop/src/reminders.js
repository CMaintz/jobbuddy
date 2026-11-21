'use strict';

// The same two types the daily email sends (DueNudgeFinder): dates she chose that have arrived.
// Deadlines and the 14-day follow-up already show in the app and would pop up on every poll.
const NOTIFIED_TYPES = new Set(['REMINDER_DUE', 'OUTREACH_FOLLOW_UP']);

function keyFor(nudge, day) {
  return `${nudge.type}:${nudge.reminderId ?? nudge.outreachId}:${day}`;
}

function messageFor(nudge) {
  if (nudge.type === 'OUTREACH_FOLLOW_UP') {
    const who = nudge.note ? ` til ${nudge.note}` : '';
    return {
      title: `Følg op hos ${nudge.companyName ?? 'en virksomhed'}`,
      body: `I dag er dagen, hvor du ville følge op på din henvendelse${who}.`,
    };
  }
  const where = [nudge.jobTitle, nudge.companyName].filter(Boolean).join(' hos ');
  return {
    title: where ? `Påmindelse: ${where}` : 'Påmindelse fra JobBuddy',
    body: nudge.note || 'Du har en påmindelse, der er klar i dag.',
  };
}

/**
 * Turns due reminders from the backend into Windows notifications. On the desktop the daily
 * email has nowhere to go (the local user's address is .invalid), so this is how she hears.
 *
 * @param {object} deps
 * @param {() => Promise<Array<object>>} deps.fetchNudges GET /api/v1/users/me/nudges
 * @param {(n: { title: string, body: string }) => void} deps.notify
 * @param {{ load(): string[], save(keys: string[]): void }} deps.seen survives restarts, so a
 *   relaunch the same day does not repeat them
 * @param {() => string} deps.today local date, YYYY-MM-DD
 */
function createReminderNotifier({ fetchNudges, notify, seen, today }) {
  return {
    /** @returns {Promise<number>} how many notifications were shown */
    async poll() {
      let nudges;
      try {
        nudges = await fetchNudges();
      } catch {
        return 0; // The next poll tries again; a restarting backend is not worth a popup.
      }
      const day = today();
      // Keys carry the day, so yesterday's are dropped here and the file never grows.
      const shown = new Set(seen.load().filter((k) => k.endsWith(`:${day}`)));
      let count = 0;
      for (const nudge of nudges.filter((n) => NOTIFIED_TYPES.has(n.type))) {
        const key = keyFor(nudge, day);
        if (shown.has(key)) continue;
        notify(messageFor(nudge));
        shown.add(key);
        count++;
      }
      seen.save([...shown]);
      return count;
    },
  };
}

module.exports = { createReminderNotifier };
