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

type PlanItemState = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'REJECTED';

type PlanFailure =
  | 'POSTING_GONE'
  | 'APPLICATION_GONE'
  | 'CHANGED_SINCE'
  | 'NOT_ALLOWED'
  | 'INTERRUPTED'
  | 'FAILED';

/** What a proposed change does, from the database: never the model's own words. */
interface PostingDetails {
  postingId: string;
  title: string | null;
  company: string | null;
}

interface StatusMoveDetails extends PostingDetails {
  applicationId: string;
  fromStatus: ApplicationStatus;
  toStatus: ApplicationStatus;
}

interface InterviewDetails extends PostingDetails {
  applicationId: string;
  interviewBefore: string | null;
  interviewAt: string;
}

/** The server sends the details type the kind takes, so checking the kind types the details. */
type PlanItemChange =
  | { kind: 'SAVE_POSTING' | 'IGNORE_POSTING' | 'GENERATE_DOCUMENTS'; details: PostingDetails }
  | { kind: 'UPDATE_STATUS'; details: StatusMoveDetails }
  | { kind: 'LOG_INTERVIEW'; details: InterviewDetails };

export type PlanItem = PlanItemChange & {
  id: string;
  state: PlanItemState;
  failure: PlanFailure | null;
  /** Where its CV and letter were written, once a GENERATE_DOCUMENTS item is done. */
  resultApplicationId: string | null;
  decidedAt: string | null;
  finishedAt: string | null;
};

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
