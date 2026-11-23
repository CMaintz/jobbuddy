'use strict';

const path = require('node:path');

/** Host part of SHELL_ORIGIN (app://jobbuddy). */
const APP_HOST = 'jobbuddy';
const UPLOADS_PREFIX = '/uploads/';

/**
 * Decides what an app://jobbuddy request is: a file of the Angular build, or an upload the
 * backend serves. Pure, so the path containment can be tested without Electron.
 *
 * @param {string} url
 * @param {string} distRoot absolute folder of the Angular build
 * @param {(file: string) => boolean} exists true for a regular file only, never a folder
 * @returns {{ kind: 'file', file: string } | { kind: 'backend', path: string } | { kind: 'notFound' }}
 */
function routeAppRequest(url, distRoot, exists) {
  const { host, pathname, search } = new URL(url);
  if (host !== APP_HOST) return { kind: 'notFound' };
  // The profile photo is <img src="/uploads/...">, relative to the page, so it arrives here.
  if (pathname.startsWith(UPLOADS_PREFIX)) return { kind: 'backend', path: pathname + search };

  let relative;
  try {
    relative = decodeURIComponent(pathname);
  } catch {
    return { kind: 'notFound' };
  }
  const root = path.resolve(distRoot);
  const file = path.resolve(root, '.' + path.sep + relative);
  // Decoded, a path can climb out with ..\ or name a drive; resolve, then insist it stayed inside.
  if (file !== root && !file.startsWith(root + path.sep)) return { kind: 'notFound' };
  if (file !== root && exists(file)) return { kind: 'file', file };
  // Anything else is an Angular route (a reload on /jobs/feed): the app routes it itself.
  return { kind: 'file', file: path.join(root, 'index.html') };
}

module.exports = { APP_HOST, routeAppRequest };
