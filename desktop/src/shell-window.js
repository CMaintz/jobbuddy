'use strict';

const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { BrowserWindow, shell } = require('electron');

const APP_URL = 'app://jobbuddy/';

/**
 * The one window. It shows the shell's own pages (setup, loading, error) or the app itself, and
 * goes nowhere else: links out open in her browser.
 *
 * @param {object} p
 * @param {ReturnType<import('./paths').resourcePaths>} p.paths
 * @param {boolean} p.startHidden launched by Windows at login
 */
function createShellWindow({ paths, startHidden }) {
  const win = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 900,
    minHeight: 600,
    show: !startHidden,
    title: 'JobBuddy',
    icon: paths.icon,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });
  const shellPagesUrl = pathToFileURL(paths.shellPages).href + '/';
  const allowed = (url) => url.startsWith(APP_URL) || url.startsWith(shellPagesUrl);

  win.webContents.on('will-navigate', (event, url) => {
    if (!allowed(url)) event.preventDefault();
  });
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  return {
    win,
    isShellPage: (url) => url.startsWith(shellPagesUrl),
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
