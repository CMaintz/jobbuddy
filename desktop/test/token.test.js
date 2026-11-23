'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { generateToken } = require('../src/token');

test('the token is long enough for the backend to start', () => {
  // DesktopToken.of refuses anything shorter than 32 characters.
  assert.ok(generateToken().length >= 32);
});

// Pins the encoding too: base64url, so the token goes in a header without escaping.
test('the token comes from the CSPRNG it is given, all of it', () => {
  const requested = [];
  const fixed = (n) => {
    requested.push(n);
    return Buffer.alloc(n, 0xab);
  };
  const token = generateToken(fixed);
  assert.deepEqual(requested, [32]);
  assert.equal(token, Buffer.alloc(32, 0xab).toString('base64url'));
});

test('two launches never share a token', () => {
  assert.notEqual(generateToken(), generateToken());
});

