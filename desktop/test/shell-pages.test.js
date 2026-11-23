'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// The shell pages run under `default-src 'self'`: an inline script or style attribute is dropped
// without a visible error, and so is a file the page names that the build did not ship.
const UI = path.join(__dirname, '..', 'ui');
const pages = fs.readdirSync(UI).filter((f) => f.endsWith('.html'));

for (const page of pages) {
  const html = fs.readFileSync(path.join(UI, page), 'utf8');

  test(`${page} has no inline script or style attribute`, () => {
    assert.doesNotMatch(html, /<script(?![^>]*\bsrc=)[^>]*>/);
    assert.doesNotMatch(html, /\sstyle=/);
  });

  test(`${page} names only files that exist`, () => {
    const refs = [...html.matchAll(/\b(?:href|src)="([^"]+)"/g)].map((m) => m[1]);
    for (const ref of refs) assert.ok(fs.existsSync(path.join(UI, ref)), `${page} -> ${ref}`);
  });
}
