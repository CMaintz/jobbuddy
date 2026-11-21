export interface ChatSummary {
  id: string;
  title: string | null;
  createdAt: string;
  lastActivityAt: string;
}

export interface ChatMessage {
  role: 'USER' | 'ASSISTANT';
  text: string;
  at: string;
}

export type ChatFailureCode =
  | 'CLAUDE_MISSING'
  | 'NOT_LOGGED_IN'
  | 'USAGE_LIMIT'
  | 'TIMEOUT'
  | 'CHAT_BUSY'
  | 'TOO_MANY_CHATS'
  | 'FAILED'
  | 'NETWORK';

export type ChatStreamEvent =
  | { type: 'delta'; text: string }
  | { type: 'done' }
  | { type: 'stopped' }
  | { type: 'error'; code: ChatFailureCode };
