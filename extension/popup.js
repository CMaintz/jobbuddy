// DEFAULT_APP_URL, APP_URL_KEY and the origin helpers come from app-origin.js.
const statusEl = document.getElementById('status');
const appUrlInput = document.getElementById('app-url');
// Kept in memory so the capture click can request the host permission synchronously,
// before any await ends its user gesture.
let savedAppUrl = DEFAULT_APP_URL;

chrome.storage.sync.get(APP_URL_KEY, (settings) => {
  savedAppUrl = normalizeAppUrl(settings[APP_URL_KEY]) || DEFAULT_APP_URL;
  appUrlInput.value = settings[APP_URL_KEY] || DEFAULT_APP_URL;
});

document.getElementById('save-url').addEventListener('click', () => {
  const appUrl = normalizeAppUrl(appUrlInput.value);
  if (!appUrl) {
    statusEl.textContent = 'Enter an http(s) URL, e.g. https://jobbuddy.example.com';
    return;
  }
  appUrlInput.value = appUrl;
  savedAppUrl = appUrl;
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

/**
 * Asks for the app origin's host permission inside the click's gesture. Users upgrading from a
 * hand-edited manifest have a custom app URL but no runtime grant; without one the bridge never
 * registers and the capture would sit unclaimed.
 */
function ensureAppAccess(appUrl) {
  const origin = appOriginOf(appUrl);
  if (isBuiltInOrigin(origin)) return Promise.resolve(true);
  return chrome.permissions.request({ origins: [originPattern(origin)] }).catch(() => false);
}

document.getElementById('capture').addEventListener('click', async () => {
  if (normalizeAppUrl(appUrlInput.value) !== savedAppUrl) {
    statusEl.textContent = 'Save the app URL first, then capture.';
    return;
  }
  const access = ensureAppAccess(savedAppUrl);
  statusEl.textContent = 'Reading the page…';
  try {
    if (!(await access)) {
      statusEl.textContent = 'Without access to ' + appOriginOf(savedAppUrl)
        + ' the capture cannot reach the app.';
      return;
    }
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    // Both injections land in the extension's isolated world of the tab, so the scraper
    // can call what job-posting.js defined.
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ['job-posting.js'] });
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
    await chrome.tabs.create({ url: savedAppUrl + '/apply' });
    window.close();
  } catch (e) {
    statusEl.textContent = 'Capture failed: this page may block extensions.';
  }
});

/**
 * Runs inside the job-posting page, after job-posting.js (which defines jobPostingFromJsonLd).
 * Prefers schema.org JobPosting JSON-LD (present on most job boards and ATS pages); falls back
 * to page heuristics.
 */
function scrapeJobPosting() {
  const htmlToText = (html) => {
    const div = document.createElement('div');
    div.innerHTML = html;
    return div.textContent.replace(/\n{3,}/g, '\n\n').trim();
  };

  const jsonLdTexts = Array.from(
    document.querySelectorAll('script[type="application/ld+json"]'), (script) => script.textContent);
  const posting = jobPostingFromJsonLd(jsonLdTexts);
  if (posting) {
    try {
      return {
        title: posting.title,
        company: posting.company,
        location: posting.location,
        description: posting.descriptionHtml ? htmlToText(posting.descriptionHtml).slice(0, 20000) : '',
      };
    } catch {
      // A page enforcing Trusted Types rejects the innerHTML assignment; its visible text
      // still holds the posting, so the heuristics below can capture it.
    }
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
