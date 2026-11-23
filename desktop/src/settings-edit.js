'use strict';

/**
 * The settings an edit saves. The form never shows the saved secrets, so a blank one means
 * "keep it", but only while it still goes to the same place: the embeddings key to the same
 * provider, the database password to the same database. Otherwise her OpenAI key would go to
 * Google, or her password to a host she just typed.
 *
 * @param {import('./settings-store').Settings | null} saved
 * @param {import('./settings-store').Settings} input from the form; secrets may be blank
 */
function mergeSettingsEdit(saved, input) {
  const merged = { ...input };
  if (!saved) return merged;
  if (!merged.dbPassword && input.dbUrl === saved.dbUrl) merged.dbPassword = saved.dbPassword;
  if (!merged.embeddingKey && input.embeddingProvider === saved.embeddingProvider) {
    merged.embeddingKey = saved.embeddingKey;
  }
  return merged;
}

module.exports = { mergeSettingsEdit };
