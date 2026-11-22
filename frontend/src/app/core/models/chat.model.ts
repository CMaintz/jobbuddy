import { ApplicationStatus } from './application.model';

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

type PlanItemKind = 'SAVE_POSTING' | 'IGNORE_POSTING' | 'UPDATE_STATUS' | 'LOG_INTERVIEW';

type PlanItemState = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'REJECTED';

type PlanFailure =
  | 'POSTING_GONE'
  | 'APPLICATION_GONE'
  | 'CHANGED_SINCE'
  | 'NOT_ALLOWED'
  | 'INTERRUPTED'
  | 'FAILED';

/** What a proposed change does, from the database: never the model's own words. */
interface PlanItemDetails {
  postingId: string | null;
  applicationId: string | null;
  title: string | null;
  company: string | null;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus | null;
  interviewBefore: string | null;
  interviewAt: string | null;
}

export interface PlanItem {
  id: string;
  kind: PlanItemKind;
  details: PlanItemDetails;
  state: PlanItemState;
  failure: PlanFailure | null;
  decidedAt: string | null;
  finishedAt: string | null;
}

/** Changes the chat proposed; nothing runs until she approves an item. */
export interface ChatPlan {
  id: string;
  createdAt: string;
  items: PlanItem[];
}

export interface ChatMessage {
  role: 'USER' | 'ASSISTANT';
  text: string;
  at: string;
  actions?: ChatAction[];
  planIds?: string[];
}

export interface ChatTranscript {
  messages: ChatMessage[];
  contextLost: boolean;
  plans?: ChatPlan[];
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
  | { type: 'plan'; plan: ChatPlan }
  | { type: 'done' }
  | { type: 'stopped' }
  | { type: 'error'; code: ChatFailureCode };
