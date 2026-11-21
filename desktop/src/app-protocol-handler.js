'use strict';

const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const { routeAppRequest } = require('./app-protocol');

const NOT_FOUND = 404;
const UNAVAILABLE = 503;

function isFile(file) {
  try {
    return fs.statSync(file).isFile();
  } catch {
    return false;
  }
}

/**
 * Serves the Angular build at app://jobbuddy, the origin the backend's CORS admits, and forwards
 * /uploads to the backend. The forwarded request goes through the default session, so the token
 * is added by token-header.js like every other backend request, and the photo stays same-origin
 * for the PDF export's canvas.
 *
 * @param {object} p
 * @param {Electron.Protocol} p.protocol
 * @param {typeof fetch} p.fetch Electron's net.fetch
 * @param {string} p.distRoot
 * @param {() => string | null} p.backendOrigin
 */
function registerAppProtocol({ protocol, fetch, distRoot, backendOrigin }) {
  protocol.handle('app', (request) => {
    const route = routeAppRequest(request.url, distRoot, isFile);
    if (route.kind === 'file') return fetch(pathToFileURL(route.file).href);
    if (route.kind === 'backend' && request.method === 'GET') {
      const origin = backendOrigin();
      if (!origin) return new Response(null, { status: UNAVAILABLE });
      return fetch(origin + route.path);
    }
    return new Response(null, { status: NOT_FOUND });
  });
}

module.exports = { registerAppProtocol };
