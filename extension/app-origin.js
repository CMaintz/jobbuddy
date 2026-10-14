// Shared by the popup, the background worker and the capture bridge (plain globals:
// content scripts and classic service workers can't use ES imports).

const DEFAULT_APP_URL = 'http://localhost:4200';
const APP_URL_KEY = 'appUrl';
const BRIDGE_SCRIPT_ID = 'jb-capture-bridge';
const BRIDGE_FILES = ['app-origin.js', 'capture-bridge.js'];
const ALLOWED_PROTOCOLS = ['http:', 'https:'];

// Origins the manifest's static content script already covers, so no permission is needed.
const BUILT_IN_ORIGINS = ['http://localhost:4200', 'http://localhost'];

/** Returns the trimmed URL without a trailing slash, or null when it isn't an http(s) URL. */
function normalizeAppUrl(raw) {
  const trimmed = String(raw || '').trim().replace(/\/+$/, '');
  if (!trimmed) return DEFAULT_APP_URL;
  try {
    return ALLOWED_PROTOCOLS.includes(new URL(trimmed).protocol) ? trimmed : null;
  } catch {
    return null;
  }
}

/** Origin of the configured app URL, falling back to the default when unset or invalid. */
function appOriginOf(appUrl) {
  return new URL(normalizeAppUrl(appUrl) || DEFAULT_APP_URL).origin;
}

/** Match pattern covering every page on the given origin. */
function originPattern(origin) {
  return origin + '/*';
}

function isBuiltInOrigin(origin) {
  return BUILT_IN_ORIGINS.includes(origin);
}
