'use strict';

/**
 * What to do at start. When Windows starts JobBuddy at login (hidden), nobody sees the window,
 * so a failed check must not hold the backend back: enrichment and reminders would stop for the
 * day. It starts anyway and the tray says what is wrong. Opened by her, the setup page shows it.
 *
 * @returns {{ start: boolean, warn: boolean }} start the backend; show a tray notification
 */
function startupPlan({ hasSettings, checksOk, hidden }) {
  if (!hasSettings) return { start: false, warn: hidden };
  if (checksOk) return { start: true, warn: false };
  return { start: hidden, warn: hidden };
}

module.exports = { startupPlan };
