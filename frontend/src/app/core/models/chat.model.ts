export interface ChatSummary {
  id: string;
  title: string | null;
  createdAt: string;
  lastActivityAt: string;
}

type ChatActionKind = 'OPEN_POSTING' | 'START_APPLICATION' | 'SHOW_PIPELINE' | 'STEP_THROUGH';

export interface ChatActionPosting {
  id: string;
  title: string | null;
  company: string | null;
}

export interface ChatAction {
  kind: ChatActionKind;
  postings: ChatActionPosting[];
}

export interface ChatMessage {
  role: 'USER' | 'ASSISTANT';
  text: string;
  at: string;
  actions?: ChatAction[];
}

export interface ChatTranscript {
  messages: ChatMessage[];
  contextLost: boolean;
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
  | { type: 'action'; action: ChatAction }
  | { type: 'done' }
  | { type: 'stopped' }
  | { type: 'error'; code: ChatFailureCode };
