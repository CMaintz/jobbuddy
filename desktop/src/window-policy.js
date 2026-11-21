'use strict';

// The security decisions about the one window, as pure functions so a widened check goes red in
// a test rather than in her session. shell-window.js and main.js wire them to Electron.

const APP_URL = 'app://jobbuddy/';
const EXTERNAL_IN_PLACE = ['mailto:', 'tel:'];
const EXTERNAL_NEW_WINDOW = ['http:', 'https:', ...EXTERNAL_IN_PLACE];
// Copy buttons in the app (application output) use navigator.clipboard.writeText.
const APP_PERMISSIONS = ['clipboard-sanitized-write'];

function protocolOf(url) {
  try {
    return new URL(url).protocol;
  } catch {
    return null;
  }
}

/**
 * What a navigation of the window may do: stay on the app or the shell's own pages, hand a mail
 * or phone link to her own programs, or nothing. Other web pages never load in this window:
 * every backend request from it carries her token.
 *
 * @param {string} url
 * @param {string} shellPagesUrl file URL of the shell pages folder, ending in "/"
 * @returns {'allow' | 'external' | 'deny'}
 */
function navigationDecision(url, shellPagesUrl) {
  if (url.startsWith(APP_URL) || url.startsWith(shellPagesUrl)) return 'allow';
  return EXTERNAL_IN_PLACE.includes(protocolOf(url)) ? 'external' : 'deny';
}

/**
 * window.open and target=_blank: web, mail and phone links open outside; nothing else does, so a
 * page cannot get Windows to run a file: or ms-*: handler.
 *
 * @returns {'external' | 'deny'}
 */
function windowOpenDecision(url) {
  return EXTERNAL_NEW_WINDOW.includes(protocolOf(url)) ? 'external' : 'deny';
}

/** IPC that changes settings or quits is for the shell pages only, never the app page. */
function isShellSender(frameUrl, shellPagesUrl) {
  return typeof frameUrl === 'string' && frameUrl.startsWith(shellPagesUrl);
}

/** Only the app page is told the backend's origin. */
function isAppSender(frameUrl) {
  return typeof frameUrl === 'string' && frameUrl.startsWith(APP_URL);
}

/** BrowserWindow options. The installed app has no DevTools: they would show her token. */
function windowOptions({ isPackaged, preload, icon, startHidden }) {
  return {
    width: 1280,
    height: 860,
    minWidth: 900,
    minHeight: 600,
    show: !startHidden,
    title: 'JobBuddy',
    icon,
    autoHideMenuBar: true,
    webPreferences: {
      preload,
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      devTools: !isPackaged,
    },
  };
}

/** No application menu in the installed app, and with it no Ctrl+Shift+I or reload shortcuts. */
function applyMenuPolicy(Menu, isPackaged) {
  if (isPackaged) Menu.setApplicationMenu(null);
}

// Electron gives permission checks an origin ("app://jobbuddy") and requests a URL. Node's URL
// reports a null origin for a custom scheme, so compare scheme and host instead.
function isAppOrigin(urlOrOrigin) {
  try {
    const { protocol, host } = new URL(urlOrOrigin);
    return protocol === 'app:' && host === 'jobbuddy';
  } catch {
    return false;
  }
}

/** Denies every web permission except clipboard writes from the app page. */
function permissionAllowed(permission, requestingUrlOrOrigin) {
  return APP_PERMISSIONS.includes(permission) && isAppOrigin(requestingUrlOrOrigin);
}

module.exports = {
  APP_URL,
  navigationDecision,
  windowOpenDecision,
  isShellSender,
  isAppSender,
  windowOptions,
  applyMenuPolicy,
  permissionAllowed,
};
