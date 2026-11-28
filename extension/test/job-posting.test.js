const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { loadScripts, plain } = require('./load-scripts');

const { jobPostingFromJsonLd } = loadScripts(['job-posting.js']);

/** Runs the extractor over JSON-LD blocks given as objects (or raw text for malformed ones). */
function extract(...blocks) {
  const texts = blocks.map((block) => (typeof block === 'string' ? block : JSON.stringify(block)));
  return plain(jobPostingFromJsonLd(texts));
}

const POSTING = {
  '@context': 'https://schema.org',
  '@type': 'JobPosting',
  title: 'Backend-udvikler',
  hiringOrganization: { '@type': 'Organization', name: 'Netcompany' },
  jobLocation: {
    '@type': 'Place',
    address: { '@type': 'PostalAddress', addressLocality: 'Aarhus', addressRegion: 'Midtjylland' },
  },
  description: '<p>Vi søger en backend-udvikler.</p>',
};

describe('jobPostingFromJsonLd', () => {
  it('reads every field of a top-level JobPosting', () => {
    assert.deepEqual(extract(POSTING), {
      title: 'Backend-udvikler',
      company: 'Netcompany',
      location: 'Aarhus, Midtjylland',
      descriptionHtml: '<p>Vi søger en backend-udvikler.</p>',
    });
  });

  it('finds the posting inside an @graph container', () => {
    const page = {
      '@context': 'https://schema.org',
      '@graph': [
        { '@type': 'WebPage', name: 'Karriere' },
        { '@type': 'Organization', name: 'Netcompany' },
        { ...POSTING, '@context': undefined },
      ],
    };

    assert.equal(extract(page).title, 'Backend-udvikler');
  });

  it('accepts a posting whose @type is a list', () => {
    assert.equal(extract({ ...POSTING, '@type': ['Thing', 'JobPosting'] }).title, 'Backend-udvikler');
  });

  it('finds the posting in a top-level array of nodes', () => {
    assert.equal(extract([{ '@type': 'BreadcrumbList' }, POSTING]).company, 'Netcompany');
  });

  it('skips malformed and unrelated blocks and reads a later one', () => {
    const result = extract('{ not json', { '@type': 'Organization', name: 'Ikke et opslag' }, POSTING);

    assert.equal(result.title, 'Backend-udvikler');
  });

  it('returns null when no block holds a JobPosting', () => {
    assert.equal(extract({ '@type': 'Organization', name: 'Netcompany' }), null);
    assert.equal(extract(), null);
  });

  it('takes the location from the first of several job locations', () => {
    const posting = {
      ...POSTING,
      jobLocation: [
        { address: { addressLocality: 'København', addressRegion: 'Hovedstaden' } },
        { address: { addressLocality: 'Aarhus', addressRegion: 'Midtjylland' } },
      ],
    };

    assert.equal(extract(posting).location, 'København, Hovedstaden');
  });

  it('joins only the address parts that are present', () => {
    const posting = { ...POSTING, jobLocation: { address: { addressLocality: 'Odense' } } };

    assert.equal(extract(posting).location, 'Odense');
  });

  it('falls back to empty strings for a bare posting', () => {
    assert.deepEqual(extract({ '@type': 'JobPosting' }), {
      title: '', company: '', location: '', descriptionHtml: '',
    });
  });
});
