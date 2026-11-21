import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ChatApiService } from '../api/chat.api';
import {
  ChatFailureCode, ChatMessage, ChatStreamEvent, ChatSummary,
} from '../models/chat.model';

const NOT_FOUND = 404;

interface Thread {
  messages: ChatMessage[];
  streaming: boolean;
  error: ChatFailureCode | null;
}

const EMPTY: Thread = { messages: [], streaming: false, error: null };

@Injectable({ providedIn: 'root' })
export class ChatStore {
  private api = inject(ChatApiService);
  private readonly threads = signal<Record<string, Thread>>({});

  readonly available = signal<boolean | null>(null);
  readonly chats = signal<ChatSummary[]>([]);
  readonly activeId = signal<string | null>(null);
  readonly active = computed(() => this.chats().find(c => c.id === this.activeId()) ?? null);
  private readonly thread = computed(() => this.threads()[this.activeId() ?? ''] ?? EMPTY);
  readonly messages = computed(() => this.thread().messages);
  readonly streaming = computed(() => this.thread().streaming);
  readonly error = computed(() => this.thread().error);

  load(): void {
    this.api.list().subscribe({
      next: chats => {
        this.available.set(true);
        this.chats.set(chats);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === NOT_FOUND) this.available.set(false);
      },
    });
  }

  newChat(): void {
    this.api.create().subscribe(chat => {
      this.chats.update(chats => [chat, ...chats]);
      this.activeId.set(chat.id);
    });
  }

  open(chatId: string): void {
    this.activeId.set(chatId);
    if (this.threads()[chatId]?.streaming) return;
    this.api.messages(chatId).subscribe({
      next: messages => this.update(chatId, t => ({ ...t, messages, error: null })),
      error: () => this.update(chatId, t => ({ ...t, error: 'FAILED' })),
    });
  }

  closeChat(): void {
    this.activeId.set(null);
  }

  send(text: string): void {
    const chatId = this.activeId();
    const message = text.trim();
    if (!chatId || !message || this.threads()[chatId]?.streaming) return;
    this.update(chatId, t => ({
      ...t,
      error: null,
      streaming: true,
      messages: [...t.messages, { role: 'USER', text: message, at: new Date().toISOString() }],
    }));
    this.api.send(chatId, message).subscribe({
      next: event => this.onEvent(chatId, event),
      error: (err: HttpErrorResponse) => this.endTurn(chatId, codeOf(err)),
      complete: () => this.endTurn(chatId, null),
    });
  }

  stop(): void {
    const chatId = this.activeId();
    if (chatId && this.threads()[chatId]?.streaming) this.api.stop(chatId).subscribe();
  }

  rename(chatId: string, title: string): void {
    this.api.rename(chatId, title).subscribe(renamed =>
      this.chats.update(chats => chats.map(c => (c.id === renamed.id ? renamed : c))));
  }

  remove(chatId: string): void {
    this.api.delete(chatId).subscribe(() => {
      this.chats.update(chats => chats.filter(c => c.id !== chatId));
      this.threads.update(({ [chatId]: _gone, ...rest }) => rest);
      if (this.activeId() === chatId) this.closeChat();
    });
  }

  private onEvent(chatId: string, event: ChatStreamEvent): void {
    switch (event.type) {
      case 'delta':
        this.updateAnswer(chatId, answer => ({ ...answer, text: answer.text + event.text }));
        break;
      case 'error':
        this.update(chatId, t => ({ ...t, error: event.code }));
        break;
      default:
        break;
    }
  }

  private updateAnswer(chatId: string, change: (answer: ChatMessage) => ChatMessage): void {
    this.update(chatId, t => {
      const last = t.messages[t.messages.length - 1];
      const messages = last?.role === 'ASSISTANT'
        ? [...t.messages.slice(0, -1), change(last)]
        : [...t.messages, change({ role: 'ASSISTANT', text: '', at: new Date().toISOString() })];
      return { ...t, messages };
    });
  }

  private endTurn(chatId: string, failure: ChatFailureCode | null): void {
    this.update(chatId, t => ({
      ...t, streaming: false, error: failure ?? t.error,
    }));
    if (!failure && !this.threads()[chatId]?.error) {
      this.api.list().subscribe(chats => this.chats.set(chats));
    }
  }

  private update(chatId: string, change: (thread: Thread) => Thread): void {
    this.threads.update(all => ({ ...all, [chatId]: change(all[chatId] ?? EMPTY) }));
  }
}

function codeOf(err: HttpErrorResponse): ChatFailureCode {
  try {
    const body = typeof err.error === 'string' ? JSON.parse(err.error) : err.error;
    return (body?.code as ChatFailureCode | undefined) ?? 'NETWORK';
  } catch {
    return 'NETWORK';
  }
}
