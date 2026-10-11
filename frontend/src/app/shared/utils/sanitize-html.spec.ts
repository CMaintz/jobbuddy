import { htmlTextContent, sanitizeRichText } from './sanitize-html';

describe('sanitizeRichText', () => {
  it('strips <img onerror>, since a crawled posting could otherwise run script', () => {
    const out = sanitizeRichText('<p>Hi<img src="x" onerror="alert(1)"></p>');
    expect(out).toBe('<p>Hi</p>');
  });

  it('drops <script> along with its body', () => {
    expect(sanitizeRichText('<p>a</p><script>alert(1)</script>')).toBe('<p>a</p>');
  });

  it('removes javascript: hrefs (links are not part of the rich-text subset)', () => {
    const out = sanitizeRichText('<p><a href="javascript:alert(1)">click</a></p>');
    expect(out).not.toContain('javascript:');
    expect(out).not.toContain('href');
    expect(out).toBe('<p>click</p>');
  });

  it('removes inline event handlers from otherwise allowed tags', () => {
    const out = sanitizeRichText('<p onclick="alert(1)"><strong onmouseover="x()">b</strong></p>');
    expect(out).toBe('<p><strong>b</strong></p>');
  });

  it('drops svg/iframe vectors', () => {
    const out = sanitizeRichText('<svg onload="alert(1)"></svg><iframe src="https://e.x"></iframe>ok');
    expect(out).toBe('ok');
  });

  it('keeps the formatting the editor produces', () => {
    const html = '<p style="text-align: center"><strong>B</strong> <em>I</em> <u>U</u> <s>S</s><br>'
      + '<span style="color: #ff0000; font-family: Georgia; font-size: 14px">styled</span></p>'
      + '<ul><li>one</li></ul><ol><li>two</li></ol>';
    expect(sanitizeRichText(html)).toBe(html);
  });

  it('keeps JD keyword marks but no other classes', () => {
    expect(sanitizeRichText('<mark class="jb-kw">Java</mark>')).toBe('<mark class="jb-kw">Java</mark>');
    expect(sanitizeRichText('<span class="fixed inset-0">x</span>')).toBe('<span>x</span>');
  });

  it('keeps only allowed style properties and drops url() values', () => {
    const out = sanitizeRichText(
      '<span style="position: fixed; color: red; background: url(https://t.x/p)">x</span>');
    expect(out).toBe('<span style="color: red">x</span>');
    expect(sanitizeRichText('<span style="font-family: url(https://t.x)">x</span>')).toBe('<span>x</span>');
  });
});

describe('htmlTextContent', () => {
  it('returns text without loading images or firing handlers', () => {
    const w = window as unknown as { __xss?: boolean };
    delete w.__xss;
    const text = htmlTextContent('<p>Hello</p><img src="x" onerror="window.__xss = true">');
    expect(text).toBe('Hello');
    return new Promise<void>(resolve => setTimeout(() => {
      expect(w.__xss).toBeUndefined();
      resolve();
    }, 50));
  });
});
