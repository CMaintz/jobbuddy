const DEFAULT_APP_URL = 'http://localhost:4200';

const statusEl = document.getElementById('status');
const appUrlInput = document.getElementById('app-url');

chrome.storage.sync.get('appUrl', ({ appUrl }) => {
  appUrlInput.value = appUrl || DEFAULT_APP_URL;
});
appUrlInput.addEventListener('change', () => {
  chrome.storage.sync.set({ appUrl: appUrlInput.value.trim().replace(/\/$/, '') || DEFAULT_APP_URL });
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
    const appUrl = appUrlInput.value.trim().replace(/\/$/, '') || DEFAULT_APP_URL;
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
