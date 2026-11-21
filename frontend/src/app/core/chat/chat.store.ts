import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ChatApiService } from '../api/chat.api';
import {
  ChatFailureCode, ChatMessage, ChatStreamEvent, ChatSummary,
} from '../models/chat.model';

const NOT_FOUND = 404;

@Injectable({ providedIn: 'root' })
export class ChatStore {
  private api = inject(ChatApiService);
  private turn: Subscription | null = null;

  readonly available = signal<boolean | null>(null);
  readonly chats = signal<ChatSummary[]>([]);
  readonly activeId = signal<string | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly streaming = signal(false);
  readonly error = signal<ChatFailureCode | null>(null);
  readonly active = computed(() => this.chats().find(c => c.id === this.activeId()) ?? null);

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
      this.show(chat.id, []);
    });
  }

  open(chatId: string): void {
    this.show(chatId, []);
    this.api.messages(chatId).subscribe({
      next: messages => {
        if (this.activeId() === chatId) this.messages.set(messages);
      },
      error: () => this.error.set('FAILED'),
    });
  }

  closeChat(): void {
    this.activeId.set(null);
    this.messages.set([]);
    this.error.set(null);
  }

  send(text: string): void {
    const chatId = this.activeId();
    const message = text.trim();
    if (!chatId || !message || this.streaming()) return;
    const now = new Date().toISOString();
    this.error.set(null);
    this.messages.update(m => [...m, { role: 'USER', text: message, at: now }]);
    this.streaming.set(true);
    this.turn = this.api.send(chatId, message).subscribe({
      next: event => this.onEvent(event),
      error: (err: HttpErrorResponse) => this.endTurn(codeOf(err)),
      complete: () => this.endTurn(null),
    });
  }

  stop(): void {
    const chatId = this.activeId();
    if (chatId && this.streaming()) this.api.stop(chatId).subscribe();
  }

  rename(chatId: string, title: string): void {
    this.api.rename(chatId, title).subscribe(renamed =>
      this.chats.update(chats => chats.map(c => (c.id === renamed.id ? renamed : c))));
  }

  remove(chatId: string): void {
    this.api.delete(chatId).subscribe(() => {
      this.chats.update(chats => chats.filter(c => c.id !== chatId));
      if (this.activeId() === chatId) this.closeChat();
    });
  }

  private show(chatId: string, messages: ChatMessage[]): void {
    this.activeId.set(chatId);
    this.messages.set(messages);
    this.error.set(null);
  }

  private onEvent(event: ChatStreamEvent): void {
    switch (event.type) {
      case 'delta':
        this.appendToAnswer(event.text);
        break;
      case 'error':
        this.error.set(event.code);
        break;
      default:
        break;
    }
  }

  private appendToAnswer(text: string): void {
    this.messages.update(messages => {
      const last = messages[messages.length - 1];
      if (last?.role === 'ASSISTANT') {
        return [...messages.slice(0, -1), { ...last, text: last.text + text }];
      }
      return [...messages, { role: 'ASSISTANT', text, at: new Date().toISOString() }];
    });
  }

  private endTurn(failure: ChatFailureCode | null): void {
    this.turn = null;
    this.streaming.set(false);
    if (failure) {
      this.error.set(failure);
      return;
    }
    if (!this.error()) this.api.list().subscribe(chats => this.chats.set(chats));
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
