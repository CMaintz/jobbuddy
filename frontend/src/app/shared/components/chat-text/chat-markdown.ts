interface Span {
  text: string;
  style: 'plain' | 'bold' | 'italic' | 'code';
}

type Block =
  | { kind: 'paragraph'; spans: Span[] }
  | { kind: 'bullets' | 'numbers'; items: Span[][] };

const BULLET = /^\s*[-*]\s+(.*)$/;
const NUMBER = /^\s*\d+[.)]\s+(.*)$/;
const HEADING = /^\s*#{1,6}\s+/;
const INLINE = /(\*\*[^*\n]+\*\*|`[^`\n]+`|\*[^*\s][^*\n]*\*|_[^_\s][^_\n]*_)/g;

export function parseChatMarkdown(text: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  const flushParagraph = () => {
    if (paragraph.length) blocks.push({ kind: 'paragraph', spans: spans(paragraph.join('\n')) });
    paragraph = [];
  };
  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    const bullet = BULLET.exec(line);
    const number = bullet ? null : NUMBER.exec(line);
    if (bullet || number) {
      flushParagraph();
      addItem(blocks, bullet ? 'bullets' : 'numbers', spans((bullet ?? number)![1]));
    } else if (line.trim() === '') {
      flushParagraph();
      blocks.push({ kind: 'paragraph', spans: [] });
    } else {
      paragraph.push(line.replace(HEADING, ''));
    }
  }
  flushParagraph();
  return blocks.filter(b => b.kind !== 'paragraph' || b.spans.length > 0);
}

function addItem(blocks: Block[], kind: 'bullets' | 'numbers', item: Span[]): void {
  const last = blocks[blocks.length - 1];
  if (last && last.kind === kind) last.items.push(item);
  else blocks.push({ kind, items: [item] });
}

function spans(text: string): Span[] {
  return text.split(INLINE).filter(part => part !== '').map(part => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return { text: part.slice(2, -2), style: 'bold' };
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return { text: part.slice(1, -1), style: 'code' };
    }
    if (/^([*_]).+\1$/.test(part)) return { text: part.slice(1, -1), style: 'italic' };
    return { text: part, style: 'plain' };
  });
}
