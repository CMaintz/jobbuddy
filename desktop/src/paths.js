'use strict';

const path = require('node:path');

/**
 * Where the bundled pieces are: next to the app when installed, in desktop/stage when run from a
 * checkout (`mise run desktop:build` fills it).
 *
 * @param {{ isPackaged: boolean }} app
 */
function resourcePaths(app) {
  const base = app.isPackaged ? process.resourcesPath : path.join(__dirname, '..', 'stage');
  return {
    java: path.join(base, 'jre', 'bin', 'java.exe'),
    jar: path.join(base, 'backend', 'jobbuddy.jar'),
    ui: path.join(base, 'ui'),
    icon: path.join(__dirname, '..', 'assets', 'icon.png'),
    shellPages: path.join(__dirname, '..', 'ui'),
  };
}

/** The backend's own default for APP_DATA_DIR on Windows (application-desktop.yml). */
function dataDirectory(env = process.env) {
  return path.join(env.LOCALAPPDATA ?? path.join(env.USERPROFILE ?? '.', 'AppData', 'Local'), 'JobBuddy');
}

module.exports = { resourcePaths, dataDirectory };
