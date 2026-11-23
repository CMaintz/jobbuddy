'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { mergeSettingsEdit } = require('../src/settings-edit');

const SAVED = {
  dbUrl: 'jdbc:postgresql://db.abc.supabase.co:5432/postgres?sslmode=require',
  dbUser: 'postgres',
  dbPassword: 'saved-db-password',
  embeddingProvider: 'openai',
  embeddingKey: 'saved-openai-key',
};

test('a blank secret keeps the saved one when nothing it belongs to changed', () => {
  const merged = mergeSettingsEdit(SAVED, { ...SAVED, dbPassword: '', embeddingKey: '' });
  assert.equal(merged.dbPassword, 'saved-db-password');
  assert.equal(merged.embeddingKey, 'saved-openai-key');
});

test('switching provider never sends the old provider key to the new one', () => {
  const merged = mergeSettingsEdit(SAVED, { ...SAVED, embeddingProvider: 'gemini', embeddingKey: '' });
  assert.equal(merged.embeddingKey, '');
});

test('a new database address never gets the old password', () => {
  const merged = mergeSettingsEdit(SAVED, {
    ...SAVED, dbUrl: 'jdbc:postgresql://other-host.example:5432/postgres', dbPassword: '',
  });
  assert.equal(merged.dbPassword, '');
});

test('secrets she types are used as typed', () => {
  const merged = mergeSettingsEdit(SAVED, { ...SAVED, dbPassword: 'new-pass', embeddingKey: 'new-key' });
  assert.equal(merged.dbPassword, 'new-pass');
  assert.equal(merged.embeddingKey, 'new-key');
});

test('a first setup has nothing to keep', () => {
  const input = { ...SAVED, dbPassword: '', embeddingKey: '' };
  assert.deepEqual(mergeSettingsEdit(null, input), input);
});
