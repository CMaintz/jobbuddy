'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createSettingsStore, validateSettings } = require('../src/settings-store');

const SETTINGS = {
  dbUrl: 'jdbc:postgresql://db.abc.supabase.co:5432/postgres?sslmode=require',
  dbUser: 'postgres',
  dbPassword: 'hunter2-db-password',
  embeddingProvider: 'openai',
  embeddingKey: 'embedding-key-for-tests',
};

// Stands in for DPAPI: reversible, and nothing of the plaintext survives in the bytes.
function fakeSafeStorage({ available = true, rotated = false } = {}) {
  const fake = {
    encrypted: 0,
    isAsyncEncryptionAvailable: async () => available,
    encryptStringAsync: async (s) => {
      fake.encrypted++;
      return Buffer.from(Buffer.from(s, 'utf8').map((b) => b ^ 0x5a));
    },
    decryptStringAsync: async (buf) => ({
      shouldReEncrypt: rotated,
      result: Buffer.from(buf.map((b) => b ^ 0x5a)).toString('utf8'),
    }),
  };
  return fake;
}

function tempFile() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jb-settings-'));
  return path.join(dir, 'connection.bin');
}

test('what is saved loads back the same', async () => {
  const file = tempFile();
  const store = createSettingsStore({ safeStorage: fakeSafeStorage(), file });
  await store.save(SETTINGS);
  assert.deepEqual((await store.load()), SETTINGS);
});

test('the file on disk holds no secret in the clear', async () => {
  const file = tempFile();
  await createSettingsStore({ safeStorage: fakeSafeStorage(), file }).save(SETTINGS);
  const raw = fs.readFileSync(file).toString('latin1');
  for (const secret of ['hunter2-db-password', 'embedding-key-for-tests', 'supabase']) {
    assert.ok(!raw.includes(secret), secret);
  }
});

test('without OS encryption it refuses to save rather than write plaintext', async () => {
  const file = tempFile();
  const store = createSettingsStore({ safeStorage: fakeSafeStorage({ available: false }), file });
  await assert.rejects(store.save(SETTINGS), /kryptering/i);
  assert.equal(fs.existsSync(file), false);
});

test('no saved connection, or one that no longer decrypts, means asking again', async () => {
  const file = tempFile();
  const store = createSettingsStore({ safeStorage: fakeSafeStorage(), file });
  assert.equal((await store.load()), null);
  fs.writeFileSync(file, Buffer.from('garbage'));
  assert.equal((await store.load()), null);
});

test('a key the OS has rotated is written again under the new one', async () => {
  const file = tempFile();
  const safeStorage = fakeSafeStorage({ rotated: true });
  const store = createSettingsStore({ safeStorage, file });
  await store.save(SETTINGS);
  assert.deepEqual(await store.load(), SETTINGS);
  assert.equal(safeStorage.encrypted, 2);
});

test('a connection she could not have meant is rejected with a Danish reason', async () => {
  assert.deepEqual(validateSettings(SETTINGS), []);
  const errors = validateSettings({
    ...SETTINGS,
    dbUrl: 'postgres://db.abc.supabase.co',
    dbPassword: '',
    embeddingProvider: 'mistral',
    embeddingKey: ' ',
  });
  assert.equal(errors.length, 4);
  assert.match(errors.join(' '), /jdbc:postgresql:\/\//);
});
