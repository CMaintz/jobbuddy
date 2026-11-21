'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { app, BrowserWindow, shell } = require('electron');
const policy = require('./window-policy');

const { APP_URL } = policy;

/**
 * The one window. It shows the shell's own pages (setup, loading, error) or the app itself, and
 * goes nowhere else: web links open in her browser, mail and phone links in her own programs.
 * The decisions are in window-policy.js; this only wires them to Electron.
 *
 * @param {object} p
 * @param {ReturnType<import('./paths').resourcePaths>} p.paths
 * @param {boolean} p.startHidden launched by Windows at login
 */
function createShellWindow({ paths, startHidden }) {
  const win = new BrowserWindow(policy.windowOptions({
    isPackaged: app.isPackaged,
    preload: path.join(__dirname, 'preload.js'),
    icon: paths.icon,
    startHidden,
  }));
  const shellPagesUrl = pathToFileURL(paths.shellPages).href + '/';

  win.webContents.on('will-navigate', (event, url) => {
    const decision = policy.navigationDecision(url, shellPagesUrl);
    if (decision === 'allow') return;
    event.preventDefault();
    if (decision === 'external') shell.openExternal(url);
  });
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (policy.windowOpenDecision(url) === 'external') shell.openExternal(url);
    return { action: 'deny' };
  });

  return {
    win,
    isShellPage: (url) => policy.isShellSender(url, shellPagesUrl),
    showPage(name, query = {}) {
      return win.loadFile(path.join(paths.shellPages, name), { query });
    },
    showApp() {
      return win.loadURL(APP_URL);
    },
    reveal() {
      if (win.isMinimized()) win.restore();
      win.show();
      win.focus();
    },
  };
}

module.exports = { APP_URL, createShellWindow };
