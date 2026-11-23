'use strict';

// Sandboxed preload: only contextBridge and ipcRenderer are available here.
const { contextBridge, ipcRenderer } = require('electron');

if (window.location.origin === 'app://jobbuddy') {
  // The Angular app learns where the backend is. Never the token: the shell adds that to each
  // request itself (token-header.js), so page script can never read or leak it.
  contextBridge.exposeInMainWorld('jobbuddyDesktop', {
    backendOrigin: ipcRenderer.sendSync('desktop:backend-origin'),
  });
} else if (window.location.protocol === 'file:') {
  // The shell's own pages (setup, loading, error).
  contextBridge.exposeInMainWorld('jobbuddyShell', {
    state: () => ipcRenderer.invoke('shell:state'),
    recheck: () => ipcRenderer.invoke('shell:recheck'),
    save: (settings) => ipcRenderer.invoke('shell:save', settings),
    continueAnyway: () => ipcRenderer.invoke('shell:continue'),
    restart: () => ipcRenderer.invoke('shell:restart'),
    editConnection: () => ipcRenderer.invoke('shell:edit-connection'),
    openLogFolder: () => ipcRenderer.invoke('shell:open-log-folder'),
    quit: () => ipcRenderer.invoke('shell:quit'),
  });
}
