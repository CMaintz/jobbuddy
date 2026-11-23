'use strict';

const PROVIDER_NAMES = { gemini: 'Gemini', openai: 'OpenAI' };

/**
 * The Danish message for a backend that could not start. A reason she can fix herself gets its
 * own sentence and the button to fix it; anything else keeps the technical reason for whoever
 * helps her.
 *
 * @param {Error & { code?: string, provider?: string }} error from startBackend's ready promise
 */
function startFailureMessage(error) {
  if (error.code === 'embedding-key-missing') {
    const name = PROVIDER_NAMES[error.provider] ?? error.provider;
    return `JobBuddy mangler nøglen til ${name}, som jobsøgningen bruger. Klik på "Ret forbindelsen", `
      + 'og indsæt nøglen.';
  }
  return `JobBuddy kunne ikke starte. (${error.message})`;
}

module.exports = { startFailureMessage };
