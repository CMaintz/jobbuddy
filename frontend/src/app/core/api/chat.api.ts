import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatPlan, ChatStreamEvent, ChatSummary, ChatTranscript } from '../models/chat.model';
import { SseParser } from '../chat/sse-parser';

const BASE = '/api/v1/chats';

/** Her generation settings, sent with an approval so the chat's documents follow them. */
export interface ApproveSettings {
  lengthPreference?: string;
  cvTemplate?: string;
  voice?: string;
  language?: string;
}

@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private http = inject(HttpClient);

  list(): Observable<ChatSummary[]> {
    return this.http.get<ChatSummary[]>(BASE);
  }

  create(): Observable<ChatSummary> {
    return this.http.post<ChatSummary>(BASE, null);
  }

  rename(chatId: string, title: string): Observable<ChatSummary> {
    return this.http.patch<ChatSummary>(`${BASE}/${chatId}`, { title });
  }

  delete(chatId: string): Observable<void> {
    return this.http.delete<void>(`${BASE}/${chatId}`);
  }

  messages(chatId: string): Observable<ChatTranscript> {
    return this.http.get<ChatTranscript>(`${BASE}/${chatId}/messages`);
  }

  /** Approves or rejects one proposed change, or with no item every waiting one; the plan as it now stands. */
  decide(planId: string, itemId: string | null, decision: 'approve' | 'reject',
    settings?: ApproveSettings): Observable<ChatPlan> {
    const target = itemId ? `${BASE}/plans/${planId}/items/${itemId}` : `${BASE}/plans/${planId}`;
    return this.http.post<ChatPlan>(`${target}/${decision}`, settings ?? null);
  }

  plan(planId: string): Observable<ChatPlan> {
    return this.http.get<ChatPlan>(`${BASE}/plans/${planId}`);
  }

  stop(chatId: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${chatId}/stop`, null);
  }

  send(chatId: string, text: string): Observable<ChatStreamEvent> {
    return new Observable<ChatStreamEvent>(subscriber => {
      const parser = new SseParser();
      let seen = 0;
      const relay = (soFar: string) => {
        for (const message of parser.push(soFar.slice(seen))) {
          subscriber.next({ type: message.event, ...JSON.parse(message.data) } as ChatStreamEvent);
        }
        seen = soFar.length;
      };
      const sub = this.http
        .post(`${BASE}/${chatId}/messages`, { text }, {
          observe: 'events', reportProgress: true, responseType: 'text',
        })
        .subscribe({
          next: (event: HttpEvent<string>) => {
            if (event.type === HttpEventType.DownloadProgress) relay(event.partialText ?? '');
            if (event.type === HttpEventType.Response) {
              relay(event.body ?? '');
              subscriber.complete();
            }
          },
          error: err => subscriber.error(err),
        });
      return () => sub.unsubscribe();
    });
  }
}
