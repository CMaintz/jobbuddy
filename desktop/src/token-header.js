'use strict';

/** Must match DesktopTokenFilter.TOKEN_HEADER on the backend. */
const TOKEN_HEADER = 'X-JobBuddy-Token';

/**
 * True only when the request goes to the backend itself. Compares whole origins, never string
 * prefixes: "http://127.0.0.1:51234@evil.example" starts with the backend's origin but goes to
 * evil.example, and "http://127.0.0.1:5123" is a prefix of another local port.
 *
 * @param {string} url
 * @param {string} backendOrigin e.g. "http://127.0.0.1:51234"
 */
function shouldAttachToken(url, backendOrigin) {
  try {
    return new URL(url).origin === backendOrigin;
  } catch {
    return false;
  }
}

/**
 * Adds the token to every request the window's session sends to the backend, including the
 * ones the page cannot add headers to itself (`<img src>`, CORS preflights). The page script
 * never sees the token.
 *
 * @param {{ onBeforeSendHeaders: Function }} webRequest Electron's `session.webRequest`
 * @param {string} backendOrigin
 * @param {string} token
 */
function installTokenHeader(webRequest, backendOrigin, token) {
  webRequest.onBeforeSendHeaders({ urls: [`${backendOrigin}/*`] }, (details, callback) => {
    if (!shouldAttachToken(details.url, backendOrigin)) {
      callback({ requestHeaders: details.requestHeaders });
      return;
    }
    callback({ requestHeaders: { ...details.requestHeaders, [TOKEN_HEADER]: token } });
  });
}

module.exports = { TOKEN_HEADER, shouldAttachToken, installTokenHeader };
