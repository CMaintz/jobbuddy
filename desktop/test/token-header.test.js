'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { TOKEN_HEADER, shouldAttachToken, installTokenHeader } = require('../src/token-header');

const BACKEND = 'http://127.0.0.1:51234';

test('backend requests get the token: API, uploads and health', () => {
  for (const path of ['/api/v1/auth/me', '/uploads/profile-photos/a.png', '/actuator/health']) {
    assert.equal(shouldAttachToken(BACKEND + path, BACKEND), true, path);
  }
});

test('nothing but the backend origin ever gets the token', () => {
  const others = [
    'https://fonts.googleapis.com/css2?family=Geist',
    'http://127.0.0.1:51235/api/v1/auth/me', // another local app on the next port
    'http://127.0.0.1:5123/api', // a prefix of the backend port
    'http://localhost:51234/api', // same socket, different origin: not what CORS admits
    'https://127.0.0.1:51234/api', // other scheme
    'http://127.0.0.1.evil.example:51234/api',
    'http://127.0.0.1:51234@evil.example/api', // userinfo trick
    'app://jobbuddy/index.html',
    'not a url',
  ];
  for (const url of others) {
    assert.equal(shouldAttachToken(url, BACKEND), false, url);
  }
});

function fakeWebRequest() {
  const calls = [];
  return {
    calls,
    onBeforeSendHeaders(filter, listener) {
      calls.push({ filter, listener });
    },
  };
}

function send(listener, url, requestHeaders = {}) {
  let result;
  listener({ url, requestHeaders }, (r) => {
    result = r;
  });
  return result.requestHeaders;
}

test('the listener only subscribes to the backend origin', () => {
  const webRequest = fakeWebRequest();
  installTokenHeader(webRequest, BACKEND, 'secret-token');
  assert.deepEqual(webRequest.calls[0].filter, { urls: [`${BACKEND}/*`] });
});

test('the listener adds the header to backend requests and keeps the others intact', () => {
  const webRequest = fakeWebRequest();
  installTokenHeader(webRequest, BACKEND, 'secret-token');
  const { listener } = webRequest.calls[0];

  const backend = send(listener, `${BACKEND}/uploads/x.png`, { Accept: 'image/*' });
  assert.equal(backend[TOKEN_HEADER], 'secret-token');
  assert.equal(backend.Accept, 'image/*');

  // The URL filter is Electron's; if a match pattern ever lets a stranger through, the listener
  // still refuses.
  const stranger = send(listener, 'http://127.0.0.1:51234@evil.example/', { Accept: '*/*' });
  assert.equal(stranger[TOKEN_HEADER], undefined);
  assert.equal(stranger.Accept, '*/*');
});
