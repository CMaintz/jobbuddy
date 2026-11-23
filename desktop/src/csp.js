'use strict';

/**
 * The Content-Security-Policy for everything served from app://jobbuddy.
 *
 * Scripts only from the app's own files, so an injected script (crawled posting text rendered
 * wrongly, say) cannot run, and no request goes anywhere but the backend. Styles allow inline
 * because Angular injects component styles as <style> elements and the editor writes style
 * attributes; that cannot run code. The desktop build turns off the critical-CSS step, whose
 * inline <script> this policy would block.
 *
 * @param {string} backendOrigin e.g. "http://127.0.0.1:51234"
 */
function contentSecurityPolicy(backendOrigin) {
  return [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    `img-src 'self' ${backendOrigin} data: blob:`,
    "font-src 'self' data:",
    `connect-src 'self' ${backendOrigin}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
}

module.exports = { contentSecurityPolicy };
