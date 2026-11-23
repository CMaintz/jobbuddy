export interface SseMessage {
  event: string;
  data: string;
}

const EVENT_FIELD = 'event:';
const DATA_FIELD = 'data:';

export class SseParser {
  private pending = '';

  push(chunk: string): SseMessage[] {
    this.pending += chunk.replace(/\r\n/g, '\n');
    const blocks = this.pending.split('\n\n');
    this.pending = blocks.pop() ?? '';
    return blocks.map(parseBlock).filter((m): m is SseMessage => m !== null);
  }
}

function parseBlock(block: string): SseMessage | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith(EVENT_FIELD)) event = line.slice(EVENT_FIELD.length).trim();
    else if (line.startsWith(DATA_FIELD)) data.push(line.slice(DATA_FIELD.length).replace(/^ /, ''));
  }
  return data.length ? { event, data: data.join('\n') } : null;
}
