'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { Notification } = require('electron');
const { createReminderNotifier } = require('./reminders');

const POLL_INTERVAL_MS = 15 * 60 * 1000;

function jsonFile(file) {
  return {
    load() {
      try {
        return JSON.parse(fs.readFileSync(file, 'utf8'));
      } catch {
        return [];
      }
    },
    save(keys) {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, JSON.stringify(keys));
    },
  };
}

/**
 * Polls the backend's due reminders every quarter hour and shows each as a Windows notification.
 * Call `poll` once the backend is up, so a reminder due today shows right after login.
 */
function startReminders({ backend, dataDir, iconPath, onClick }) {
  const notifier = createReminderNotifier({
    fetchNudges: () => backend.getJson('/api/v1/users/me/nudges'),
    notify: ({ title, body }) => {
      if (!Notification.isSupported()) return;
      const note = new Notification({ title, body, icon: iconPath });
      note.on('click', onClick);
      note.show();
    },
    seen: jsonFile(path.join(dataDir, 'notified-reminders.json')),
    // sv-SE formats a local date as YYYY-MM-DD.
    today: () => new Date().toLocaleDateString('sv-SE'),
  });
  const poll = () => (backend.origin() ? notifier.poll() : Promise.resolve(0));
  setInterval(poll, POLL_INTERVAL_MS);
  return { poll };
}

module.exports = { startReminders };
