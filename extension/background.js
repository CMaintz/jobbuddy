// Keeps the capture bridge registered on the user's configured app origin, so hosting
// Jobbuddy somewhere other than localhost needs no manifest edit. The popup asks for the
// host permission (that needs a user gesture); this worker does the registration, since
// the popup may close while the permission dialog is open.
importScripts('app-origin.js');

async function unregisterBridge() {
  const existing = await chrome.scripting.getRegisteredContentScripts({ ids: [BRIDGE_SCRIPT_ID] });
  if (existing.length) await chrome.scripting.unregisterContentScripts({ ids: [BRIDGE_SCRIPT_ID] });
}

/** Idempotently points the dynamic bridge at the configured origin, or removes it. */
async function syncBridgeRegistration() {
  const settings = await chrome.storage.sync.get(APP_URL_KEY);
  const origin = appOriginOf(settings[APP_URL_KEY]);
  await unregisterBridge();
  if (isBuiltInOrigin(origin)) return;
  const origins = [originPattern(origin)];
  if (!(await chrome.permissions.contains({ origins }))) return;
  await chrome.scripting.registerContentScripts([{
    id: BRIDGE_SCRIPT_ID,
    matches: origins,
    js: BRIDGE_FILES,
    runAt: 'document_start',
  }]);
}

/** Drops the host permission for an app origin the user has moved away from. */
async function releaseOrigin(oldAppUrl, newAppUrl) {
  const oldOrigin = appOriginOf(oldAppUrl);
  if (isBuiltInOrigin(oldOrigin) || oldOrigin === appOriginOf(newAppUrl)) return;
  await chrome.permissions.remove({ origins: [originPattern(oldOrigin)] });
}

// Serialized so overlapping triggers can't race unregister against register.
let pendingSync = Promise.resolve();

function sync() {
  pendingSync = pendingSync
    .then(syncBridgeRegistration)
    .catch((e) => console.warn('Capture bridge registration failed', e));
}

// Dynamic content scripts don't survive an extension reload or update.
chrome.runtime.onInstalled.addListener(sync);
chrome.runtime.onStartup.addListener(sync);
chrome.permissions.onAdded.addListener(sync);
chrome.storage.onChanged.addListener((changes, area) => {
  const change = changes[APP_URL_KEY];
  if (area !== 'sync' || !change) return;
  releaseOrigin(change.oldValue, change.newValue)
    .catch((e) => console.warn('Releasing the old app origin failed', e))
    .finally(sync);
});
