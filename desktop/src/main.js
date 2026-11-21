'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { app, ipcMain, net, Notification, protocol, safeStorage, session, shell } = require('electron');
const { resourcePaths, dataDirectory } = require('./paths');
const { runChecks } = require('./first-run-checks');
const { runCommand } = require('./run-command');
const { createSettingsStore, validateSettings } = require('./settings-store');
const { createBackendSession } = require('./backend-session');
const { createShellWindow, APP_URL } = require('./shell-window');
const { registerAppProtocol } = require('./app-protocol-handler');
const { createTray, HIDDEN_ARG } = require('./tray');
const { startReminders } = require('./reminder-loop');

// Must equal electron-builder's appId, or Windows drops the toasts of the installed app.
const APP_ID = 'dk.maintz.jobbuddy';

// Before "ready". `standard` gives the page a real origin (app://jobbuddy) instead of "null",
// which the backend's CORS would refuse; `secure` makes it a secure context.
protocol.registerSchemesAsPrivileged([{
  scheme: 'app',
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true },
}]);

const state = { quitting: false, stopped: false, toldAboutTray: false, settings: null, checks: [] };

// A second JVM against the same database would run every sweep twice: show the first instead.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => state.window?.reveal());
  app.whenReady().then(boot);
}

async function boot() {
  app.setAppUserModelId(APP_ID);
  const paths = resourcePaths(app);
  const dataDir = dataDirectory();
  const store = createSettingsStore({
    safeStorage, file: path.join(app.getPath('userData'), 'connection.bin'),
  });
  const backend = createBackendSession({
    session: session.defaultSession, fetch: net.fetch, paths, dataDir,
    onCrash: () => showError('JobBuddy stoppede uventet.'),
  });
  const window = createShellWindow({ paths, startHidden: process.argv.includes(HIDDEN_ARG) });
  Object.assign(state, { paths, dataDir, store, backend, window });

  registerAppProtocol({ protocol, fetch: net.fetch, distRoot: paths.ui, backendOrigin: backend.origin });
  registerIpc();
  state.tray = createTray({ app, iconPath: paths.icon, onOpen: window.reveal, onQuit: () => app.quit() });
  window.win.on('close', keepRunningInTray);
  state.reminders = startReminders({ backend, dataDir, iconPath: paths.icon, onClick: window.reveal });

  state.settings = await store.load();
  state.checks = await check();
  if (state.settings && state.checks.every((c) => c.ok)) await startApp();
  else await window.showPage('setup.html');
}

function check() {
  return runChecks({ env: process.env, exists: isFile, run: runCommand });
}

function isFile(file) {
  try {
    return fs.statSync(file).isFile();
  } catch {
    return false;
  }
}

async function startApp() {
  await state.window.showPage('loading.html');
  try {
    await state.backend.start(state.settings);
    await state.window.showApp();
    state.reminders.poll();
  } catch (error) {
    await showError(`JobBuddy kunne ikke starte. (${error.message})`);
  }
}

function showError(message) {
  state.window.reveal();
  return state.window.showPage('error.html', { message, log: state.backend.logPath });
}

function keepRunningInTray(event) {
  if (state.quitting) return;
  event.preventDefault();
  state.window.win.hide();
  if (!state.toldAboutTray && Notification.isSupported()) {
    state.toldAboutTray = true;
    new Notification({
      title: 'JobBuddy kører videre',
      body: 'JobBuddy arbejder videre i baggrunden. Find det ved uret nederst til højre.',
      icon: state.paths.icon,
    }).show();
  }
}

// Kill the JVM, and every Claude CLI it runs, before the app goes. Electron would otherwise exit
// and leave java.exe running with no window and no tray.
app.on('before-quit', (event) => {
  state.quitting = true;
  if (state.stopped || !state.backend) return;
  event.preventDefault();
  state.backend.stop().finally(() => {
    state.stopped = true;
    app.quit();
  });
});

function registerIpc() {
  const fromShellPage = (event) => state.window.isShellPage(event.senderFrame?.url ?? '');
  const handle = (channel, fn) => ipcMain.handle(channel, (event, ...args) => {
    if (!fromShellPage(event)) throw new Error('Not allowed');
    return fn(...args);
  });

  ipcMain.on('desktop:backend-origin', (event) => {
    event.returnValue = (event.senderFrame?.url ?? '').startsWith(APP_URL) ? state.backend.origin() : null;
  });
  handle('shell:state', () => ({
    checks: state.checks,
    hasSettings: Boolean(state.settings),
    // Prefill everything but the secrets, which never go back to a page.
    saved: state.settings && {
      dbUrl: state.settings.dbUrl, dbUser: state.settings.dbUser,
      embeddingProvider: state.settings.embeddingProvider,
    },
  }));
  handle('shell:recheck', async () => {
    state.checks = await check();
    return state.checks;
  });
  handle('shell:save', async (input) => {
    const settings = { ...input };
    // Blank secrets on an edit keep the saved ones.
    if (state.settings) {
      if (!settings.dbPassword) settings.dbPassword = state.settings.dbPassword;
      if (!settings.embeddingKey) settings.embeddingKey = state.settings.embeddingKey;
    }
    const errors = validateSettings(settings);
    if (errors.length) return { errors };
    await state.store.save(settings);
    state.settings = settings;
    startApp();
    return { errors: [] };
  });
  handle('shell:continue', () => (state.settings ? startApp() : null));
  handle('shell:restart', () => startApp());
  handle('shell:edit-connection', () => state.window.showPage('setup.html', { edit: '1' }));
  handle('shell:open-log-folder', () => shell.openPath(path.dirname(state.backend.logPath)));
  handle('shell:quit', () => app.quit());
}
