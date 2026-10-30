const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { loadScripts, plain } = require('./load-scripts');

const DESCRIPTION_HTML = '<p>Vi søger en backend-udvikler.</p>';
const DESCRIPTION_TEXT = 'Vi søger en backend-udvikler.';
const POSTING_JSON_LD = JSON.stringify({
  '@type': 'JobPosting',
  title: 'Backend-udvikler',
  hiringOrganization: { name: 'Netcompany' },
  description: DESCRIPTION_HTML,
});
const PAGE_TEXT = 'Backend-udvikler hos Netcompany. Vi søger en backend-udvikler.';

/**
 * Loads the popup with job-posting.js injected first, as the capture does, on a page carrying
 * one JSON-LD posting. `innerHTML` stands in for the page's handling of that assignment.
 */
function scrapeOnPage({ innerHTML }) {
  const control = { addEventListener: () => {} };
  const page = {
    getElementById: () => control,
    querySelectorAll: () => [{ textContent: POSTING_JSON_LD }],
    querySelector: (selector) => (selector === 'h1' ? { innerText: 'Backend-udvikler' } : null),
    body: { innerText: PAGE_TEXT },
    title: 'Job',
    createElement: () => ({
      set innerHTML(html) {
        innerHTML(html);
        // The stub knows the one description it is given rather than parsing HTML.
        this.textContent = html === DESCRIPTION_HTML ? DESCRIPTION_TEXT : html;
      },
    }),
  };
  const scope = loadScripts(['app-origin.js', 'job-posting.js', 'popup.js'], {
    document: page,
    window: { getSelection: () => '' },
    chrome: { storage: { sync: { get: () => {} } } },
  });
  return plain(scope.scrapeJobPosting());
}

describe('scrapeJobPosting', () => {
  it('reads the JSON-LD posting and turns its description into text', () => {
    const result = scrapeOnPage({ innerHTML: () => {} });

    assert.equal(result.company, 'Netcompany');
    assert.equal(result.description, DESCRIPTION_TEXT);
  });

  it('falls back to the page text when the description cannot be converted', () => {
    const result = scrapeOnPage({
      innerHTML: () => { throw new TypeError('This document requires TrustedHTML assignment.'); },
    });

    assert.equal(result.title, 'Backend-udvikler');
    assert.equal(result.description, PAGE_TEXT);
  });
});
