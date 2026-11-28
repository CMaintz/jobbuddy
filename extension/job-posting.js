// schema.org JobPosting extraction, free of DOM and chrome APIs so it also runs under
// `node --test`. The popup injects this file into the job page just before scrapeJobPosting,
// which executeScript serializes on its own and so cannot close over helpers in popup.js.
// Function declarations only: the file is re-injected on every capture, and a repeated
// top-level const in the same page world would throw.

/** Depth-first search for the first JobPosting node, through arrays and @graph containers. */
function findJobPosting(node) {
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
}

/** "Locality, Region" of the first jobLocation (a single place or a list); '' when absent. */
function jobPostingLocation(posting) {
  const address = [].concat(posting.jobLocation || [])[0]?.address;
  if (!address) return '';
  return [address.addressLocality, address.addressRegion].filter(Boolean).join(', ');
}

/**
 * Reads the first JobPosting out of a page's JSON-LD script texts, or null when none has one.
 * The description stays HTML; the caller turns it into text inside the page. A malformed block
 * is skipped rather than ending the search, since pages often carry several unrelated ones.
 */
function jobPostingFromJsonLd(jsonLdTexts) {
  for (const text of jsonLdTexts) {
    let parsed;
    try {
      parsed = JSON.parse(text);
    } catch {
      continue;
    }
    const posting = findJobPosting(parsed);
    if (!posting) continue;
    return {
      title: posting.title || '',
      company: posting.hiringOrganization?.name || '',
      location: jobPostingLocation(posting),
      descriptionHtml: posting.description || '',
    };
  }
  return null;
}
