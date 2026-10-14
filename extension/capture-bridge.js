// Runs at document_start on the configured Jobbuddy origin (and the built-in localhost
// origins). Moves a pending capture from extension storage into the page's localStorage,
// where the apply screen picks it up (key: jb-captured-job). No backend auth needed: the
// user's existing app session does the actual job creation.
const CAPTURE_STORAGE_KEY = 'jb-captured-job';
const CAPTURE_READY_EVENT = 'jb-capture-ready';

chrome.storage.sync.get(APP_URL_KEY, (settings) => {
  // Only the configured app gets the capture; any other matching page leaves it parked.
  if (location.origin !== appOriginOf(settings[APP_URL_KEY])) return;
  chrome.storage.local.get('pendingCapture', ({ pendingCapture }) => {
    if (!pendingCapture) return;
    try {
      localStorage.setItem(CAPTURE_STORAGE_KEY, JSON.stringify(pendingCapture));
    } catch { /* storage blocked, nothing to hand off */ }
    chrome.storage.local.remove('pendingCapture');
    // The app may already have booted by the time this async read resolves
    window.dispatchEvent(new CustomEvent(CAPTURE_READY_EVENT));
  });
});
