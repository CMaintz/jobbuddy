'use strict';

const crypto = require('node:crypto');

// 32 random bytes encode to 43 base64url characters: above DesktopToken.MIN_LENGTH (32) on the
// backend, and header-safe without escaping.
const TOKEN_BYTES = 32;

/**
 * A fresh secret for one launch of the backend. Never written to disk or logged; it lives in this
 * process and the backend's environment only.
 *
 * @param {(size: number) => Buffer} randomBytes a CSPRNG; injectable for tests only
 * @returns {string}
 */
function generateToken(randomBytes = crypto.randomBytes) {
  return randomBytes(TOKEN_BYTES).toString('base64url');
}

module.exports = { generateToken };
