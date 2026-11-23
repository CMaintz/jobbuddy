'use strict';

const { Menu, Tray, nativeImage } = require('electron');

// The login item starts the app with this flag, so it comes up in the tray, not in her face.
const HIDDEN_ARG = '--hidden';

/**
 * Tray icon with "Åbn" and "Afslut". Closing the window keeps the app here, so enrichment and
 * reminders carry on while the PC is on.
 */
function createTray({ app, iconPath, onOpen, onQuit }) {
  const icon = nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 });
  const tray = new Tray(icon);
  const loginItem = { args: [HIDDEN_ARG] };

  const rebuild = () => {
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Åbn', click: onOpen },
      {
        label: 'Start med Windows',
        type: 'checkbox',
        checked: app.getLoginItemSettings(loginItem).openAtLogin,
        click: (item) => {
          app.setLoginItemSettings({ ...loginItem, openAtLogin: item.checked });
          rebuild();
        },
      },
      { type: 'separator' },
      { label: 'Afslut', click: onQuit },
    ]));
  };
  tray.setToolTip('JobBuddy');
  tray.on('double-click', onOpen);
  rebuild();
  return tray;
}

module.exports = { HIDDEN_ARG, createTray };
