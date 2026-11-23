'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * @typedef {object} Settings
 * @property {string} dbUrl JDBC URL of her Supabase database
 * @property {string} dbUser
 * @property {string} dbPassword
 * @property {'openai' | 'gemini'} embeddingProvider must match what the scheduled crawl embeds with
 * @property {string} embeddingKey
 */

const FIELDS = ['dbUrl', 'dbUser', 'dbPassword', 'embeddingProvider', 'embeddingKey'];
const PROVIDERS = ['openai', 'gemini'];

/**
 * Plain Danish reasons the settings cannot be used; empty when they can.
 *
 * @param {Partial<Settings>} s
 * @returns {string[]}
 */
function validateSettings(s) {
  const errors = [];
  if (!String(s.dbUrl ?? '').startsWith('jdbc:postgresql://')) {
    errors.push('Databaseadressen skal starte med jdbc:postgresql://');
  }
  if (!String(s.dbUser ?? '').trim()) errors.push('Skriv databasens brugernavn.');
  if (!String(s.dbPassword ?? '')) errors.push('Skriv databasens adgangskode.');
  if (!PROVIDERS.includes(s.embeddingProvider)) errors.push('Vælg OpenAI eller Gemini.');
  if (!String(s.embeddingKey ?? '').trim()) errors.push('Skriv nøglen til søgningen.');
  return errors;
}

/**
 * Her connection details, encrypted with Electron safeStorage (DPAPI on Windows, so only her
 * Windows account can read them) in her own profile. They never go in the installer or the repo.
 *
 * @param {object} deps
 * @param {Electron.SafeStorage} deps.safeStorage the async API; the sync one is deprecated in Electron 45
 * @param {string} deps.file where the encrypted blob lives
 */
function createSettingsStore({ safeStorage, file }) {
  /** @param {Settings} settings */
  async function save(settings) {
    // No working OS encryption means not storing a database password at all, never a fallback.
    if (!(await safeStorage.isAsyncEncryptionAvailable())) {
      throw new Error('Windows-kryptering er ikke tilgængelig, så forbindelsen kan ikke gemmes.');
    }
    const picked = Object.fromEntries(FIELDS.map((f) => [f, settings[f]]));
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, await safeStorage.encryptStringAsync(JSON.stringify(picked)));
  }

  /** @returns {Promise<Settings | null>} null when nothing usable is saved, so the shell asks again */
  async function load() {
    let decrypted;
    try {
      decrypted = await safeStorage.decryptStringAsync(fs.readFileSync(file));
    } catch {
      // Missing, written under another Windows account, or damaged: asking again is the only fix.
      return null;
    }
    let settings;
    try {
      settings = JSON.parse(decrypted.result);
    } catch {
      return null;
    }
    if (validateSettings(settings).length > 0) return null;
    if (decrypted.shouldReEncrypt) await save(settings);
    return settings;
  }

  return { save, load };
}

module.exports = { createSettingsStore, validateSettings };
