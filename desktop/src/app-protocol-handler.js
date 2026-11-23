'use strict';

const fs = require('node:fs');
const { pathToFileURL } = require('node:url');
const { routeAppRequest } = require('./app-protocol');
const { contentSecurityPolicy } = require('./csp');

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
 * for the PDF export's canvas. Every file it serves carries the app's Content-Security-Policy.
 *
 * @param {object} p
 * @param {Electron.Protocol} p.protocol
 * @param {typeof fetch} p.fetch Electron's net.fetch
 * @param {string} p.distRoot
 * @param {() => string | null} p.backendOrigin
 */
async function withPolicy(response, backendOrigin) {
  const headers = new Headers(response.headers);
  headers.set('Content-Security-Policy', contentSecurityPolicy(backendOrigin ?? ''));
  return new Response(response.body, { status: response.status, headers });
}

function registerAppProtocol({ protocol, fetch, distRoot, backendOrigin }) {
  protocol.handle('app', async (request) => {
    const route = routeAppRequest(request.url, distRoot, isFile);
    if (route.kind === 'file') return withPolicy(await fetch(pathToFileURL(route.file).href), backendOrigin());
    if (route.kind === 'backend' && request.method === 'GET') {
      const origin = backendOrigin();
      if (!origin) return new Response(null, { status: UNAVAILABLE });
      return fetch(origin + route.path);
    }
    return new Response(null, { status: NOT_FOUND });
  });
}

module.exports = { registerAppProtocol };
