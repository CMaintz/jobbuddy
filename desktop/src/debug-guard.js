'use strict';

// No Electron fuse covers these: they are Chromium switches, read before any app code runs.
const DEBUG_SWITCHES = ['remote-debugging-port', 'remote-debugging-pipe'];

/**
 * Stops the installed app when started with a remote debugging switch. Whoever could connect
 * could drive the window, and every backend request from it carries her token. A build run from
 * the checkout (not packaged) keeps them, for the CDP harness and DevTools.
 *
 * @param {Electron.App} app
 * @returns {boolean} true when the app is exiting
 */
function refuseRemoteDebugging(app) {
  if (!app.isPackaged || !DEBUG_SWITCHES.some((name) => app.commandLine.hasSwitch(name))) return false;
  app.exit(1);
  return true;
}

module.exports = { refuseRemoteDebugging };
