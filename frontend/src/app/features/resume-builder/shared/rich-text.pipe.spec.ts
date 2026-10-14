import { SecurityContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';
import { RichTextPipe } from './rich-text.pipe';

describe('RichTextPipe', () => {
  let pipe: RichTextPipe;
  let rendered: (value: string | null) => string;

  beforeEach(() => {
    pipe = TestBed.runInInjectionContext(() => new RichTextPipe());
    const sanitizer = TestBed.inject(DomSanitizer);
    // What [innerHTML] receives: the pipe output is trusted, so it passes through verbatim.
    rendered = value => sanitizer.sanitize(SecurityContext.HTML, pipe.transform(value)) ?? '';
  });

  it('strips script, event handlers and javascript: links from posting HTML', () => {
    const out = rendered(
      '<p onclick="x()">Role<img src=x onerror="alert(1)"><script>alert(2)</script>'
      + '<a href="javascript:alert(3)">apply</a></p>');
    expect(out).toBe('<p>Roleapply</p>');
  });

  it('keeps editor formatting including inline styles', () => {
    const html = '<p style="text-align: right"><span style="color: #123456">c</span> <strong>b</strong></p>';
    expect(rendered(html)).toBe(html);
  });

  it('wraps plain text lines in escaped paragraphs', () => {
    expect(rendered('R&D\nnext')).toBe('<p>R&amp;D</p><p>next</p>');
  });

  it('renders nothing for null', () => {
    expect(rendered(null)).toBe('');
  });
});
