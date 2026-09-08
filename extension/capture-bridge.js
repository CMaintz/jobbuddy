// Runs on the Jobbuddy origin at document_start. Moves a pending capture from
// extension storage into the page's localStorage, where the apply screen picks
// it up (key: jb-captured-job). No backend auth needed — the user's existing
// app session does the actual job creation.
chrome.storage.local.get('pendingCapture', ({ pendingCapture }) => {
  if (!pendingCapture) return;
  try {
    localStorage.setItem('jb-captured-job', JSON.stringify(pendingCapture));
  } catch { /* storage blocked — nothing to hand off */ }
  chrome.storage.local.remove('pendingCapture');
  // The app may already have booted by the time this async read resolves
  window.dispatchEvent(new CustomEvent('jb-capture-ready'));
});
