// DEFAULT_APP_URL, APP_URL_KEY and the origin helpers come from app-origin.js.
const statusEl = document.getElementById('status');
const appUrlInput = document.getElementById('app-url');

chrome.storage.sync.get(APP_URL_KEY, (settings) => {
  appUrlInput.value = settings[APP_URL_KEY] || DEFAULT_APP_URL;
});

document.getElementById('save-url').addEventListener('click', () => {
  const appUrl = normalizeAppUrl(appUrlInput.value);
  if (!appUrl) {
    statusEl.textContent = 'Enter an http(s) URL, e.g. https://jobbuddy.example.com';
    return;
  }
  appUrlInput.value = appUrl;
  // Not awaited: the permission request must run inside this click's user gesture, and the
  // popup may close while its dialog is open. The background worker registers the bridge.
  chrome.storage.sync.set({ [APP_URL_KEY]: appUrl });
  const origin = appOriginOf(appUrl);
  if (isBuiltInOrigin(origin)) {
    statusEl.textContent = 'Saved.';
    return;
  }
  chrome.permissions.request({ origins: [originPattern(origin)] }).then((granted) => {
    statusEl.textContent = granted
      ? 'Saved. Captures will open in ' + origin + '.'
      : 'Saved, but without access to ' + origin + ' captures cannot reach the app.';
  }).catch(() => {
    statusEl.textContent = 'Saved, but access to ' + origin + ' could not be requested.';
  });
});

document.getElementById('capture').addEventListener('click', async () => {
  statusEl.textContent = 'Reading the page…';
  try {
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    const [injection] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: scrapeJobPosting,
    });
    const data = injection?.result;
    if (!data || (!data.title && !data.description)) {
      statusEl.textContent = 'No job posting found on this page.';
      return;
    }
    await chrome.storage.local.set({
      pendingCapture: { ...data, url: tab.url, savedAt: Date.now() },
    });
    // Open the saved app URL: the bridge only hands the capture to that origin.
    const settings = await chrome.storage.sync.get(APP_URL_KEY);
    const appUrl = normalizeAppUrl(settings[APP_URL_KEY]) || DEFAULT_APP_URL;
    await chrome.tabs.create({ url: appUrl + '/apply' });
    window.close();
  } catch (e) {
    statusEl.textContent = 'Capture failed — this page may block extensions.';
  }
});

/**
 * Runs inside the job-posting page. Prefers schema.org JobPosting JSON-LD
 * (present on most job boards and ATS pages); falls back to page heuristics.
 */
function scrapeJobPosting() {
  const htmlToText = (html) => {
    const div = document.createElement('div');
    div.innerHTML = html;
    return div.textContent.replace(/\n{3,}/g, '\n\n').trim();
  };

  const findJobPosting = (node) => {
    if (!node || typeof node !== 'object') return null;
    if (Array.isArray(node)) {
      for (const item of node) {
        const found = findJobPosting(item);
        if (found) return found;
      }
      return null;
    }
    const type = node['@type'];
    if (type === 'JobPosting' || (Array.isArray(type) && type.includes('JobPosting'))) return node;
    return findJobPosting(node['@graph']);
  };

  for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
    try {
      const posting = findJobPosting(JSON.parse(script.textContent));
      if (!posting) continue;
      const address = [].concat(posting.jobLocation || [])[0]?.address;
      const location = address
        ? [address.addressLocality, address.addressRegion].filter(Boolean).join(', ')
        : '';
      return {
        title: posting.title || '',
        company: posting.hiringOrganization?.name || '',
        location,
        description: posting.description ? htmlToText(posting.description).slice(0, 20000) : '',
      };
    } catch { /* malformed JSON-LD — try the next block */ }
  }

  // Heuristic fallback: user selection first, then the page's main content
  const selection = String(window.getSelection() || '').trim();
  const main = document.querySelector('article, main, [role="main"]') || document.body;
  const description = (selection.length > 200 ? selection : main.innerText || '')
    .replace(/\n{3,}/g, '\n\n').trim().slice(0, 20000);

  const ogTitle = document.querySelector('meta[property="og:title"]')?.content;
  const siteName = document.querySelector('meta[property="og:site_name"]')?.content;
  return {
    title: (document.querySelector('h1')?.innerText || ogTitle || document.title || '').trim().slice(0, 200),
    company: (siteName || '').trim().slice(0, 120),
    location: '',
    description,
  };
}
