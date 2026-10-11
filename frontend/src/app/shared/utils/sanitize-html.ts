import DOMPurify from 'dompurify';

/** Tags the TipTap editor emits (StarterKit minus headings/code blocks/quotes/rules), plus keyword marks. */
const RICH_TEXT_TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 's', 'code', 'ul', 'ol', 'li', 'span', 'mark'];
const RICH_TEXT_ATTRS = ['style', 'class'];

/** Inline styles the editor's colour, font-family, font-size and text-align controls write. */
const ALLOWED_STYLE_PROPS = new Set(['color', 'font-family', 'font-size', 'text-align']);
/** The only class rendered markup carries: the JD-keyword highlight. */
const ALLOWED_CLASSES = new Set(['jb-kw']);
/** CSS that can fetch or execute even inside an allowed property. */
const UNSAFE_CSS_VALUE = /url\s*\(|expression\s*\(|javascript:|@import/i;

const purifier = DOMPurify();
purifier.addHook('uponSanitizeAttribute', (_node, data) => {
  if (data.attrName === 'style') data.attrValue = filterStyle(data.attrValue);
  if (data.attrName === 'class') data.attrValue = filterClasses(data.attrValue);
  if (!data.attrValue) data.keepAttr = false;
});

function filterStyle(style: string): string {
  return style.split(';')
    .map(decl => decl.trim())
    .filter(decl => {
      const colon = decl.indexOf(':');
      if (colon < 0) return false;
      const prop = decl.slice(0, colon).trim().toLowerCase();
      return ALLOWED_STYLE_PROPS.has(prop) && !UNSAFE_CSS_VALUE.test(decl.slice(colon + 1));
    })
    .join('; ');
}

function filterClasses(classes: string): string {
  return classes.split(/\s+/).filter(c => ALLOWED_CLASSES.has(c)).join(' ');
}

/**
 * Reduces untrusted HTML (crawled postings, AI output, pasted text) to the rich-text formatting
 * subset. The result is safe to trust for [innerHTML]; anything outside the allowlist is dropped.
 */
export function sanitizeRichText(html: string): string {
  return purifier.sanitize(html, {
    ALLOWED_TAGS: RICH_TEXT_TAGS,
    ALLOWED_ATTR: RICH_TEXT_ATTRS,
    ALLOW_DATA_ATTR: false,
  });
}

/**
 * Text content of an HTML fragment. Parses into an inert document: assigning innerHTML on an
 * element of the live document would load `<img src>` and fire its onerror even when detached.
 */
export function htmlTextContent(html: string): string {
  return new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
}
