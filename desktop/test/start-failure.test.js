'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { startFailureMessage } = require('../src/start-failure');

test('a missing embeddings key is told in plain Danish, naming the service', () => {
  const error = Object.assign(new Error('x'), { code: 'embedding-key-missing', provider: 'gemini' });
  const message = startFailureMessage(error);
  assert.match(message, /Gemini/);
  assert.match(message, /nøgle/i);
  assert.match(message, /Ret forbindelsen/);
  assert.doesNotMatch(message, /GEMINI_API_KEY|embedding-key-missing/);
});

test('OpenAI is named as OpenAI', () => {
  const error = Object.assign(new Error('x'), { code: 'embedding-key-missing', provider: 'openai' });
  assert.match(startFailureMessage(error), /OpenAI/);
});

test('anything else keeps the general message with the technical reason', () => {
  assert.equal(startFailureMessage(new Error('The backend did not start in time')),
    'JobBuddy kunne ikke starte. (The backend did not start in time)');
});
