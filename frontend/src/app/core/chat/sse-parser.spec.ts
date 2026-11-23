import { SseParser } from './sse-parser';

describe('SseParser', () => {
  it('reads each event once, even when it arrives split across chunks', () => {
    const parser = new SseParser();

    const first = parser.push('event:delta\ndata:{"text":"Hej "}\n\nevent:del');
    const second = parser.push('ta\ndata:{"text":"med dig"}\n');
    const third = parser.push('\nevent:done\ndata:{}\n\n');

    expect(first).toEqual([{ event: 'delta', data: '{"text":"Hej "}' }]);
    expect(second).toEqual([]);
    expect(third).toEqual([
      { event: 'delta', data: '{"text":"med dig"}' },
      { event: 'done', data: '{}' },
    ]);
  });

  it('joins multi-line data and accepts CRLF line ends', () => {
    const parser = new SseParser();

    expect(parser.push('event:delta\r\ndata:a\r\ndata:b\r\n\r\n')).toEqual([
      { event: 'delta', data: 'a\nb' },
    ]);
  });
});
