import { Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type NoticeTone = 'success' | 'error';

export interface Notice {
  message: string;
  tone: NoticeTone;
}

/**
 * App-wide toast, rendered once by the app shell. Callers pass a translation key so a failure
 * is never silent and never shown untranslated.
 */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private translate = inject(TranslateService);
  private readonly _notice = signal<Notice | null>(null);
  readonly notice = this._notice.asReadonly();

  error(key: string, params?: Record<string, unknown>): void {
    this.show(key, 'error', params);
  }

  success(key: string, params?: Record<string, unknown>): void {
    this.show(key, 'success', params);
  }

  /** Shows a message that is already translated or comes from the server as-is. */
  errorText(message: string): void {
    this._notice.set({ message, tone: 'error' });
  }

  /** An error callback for `subscribe({ error })` that reports `key` and swallows the error. */
  onError(key: string): () => void {
    return () => this.error(key);
  }

  dismiss(): void {
    this._notice.set(null);
  }

  private show(key: string, tone: NoticeTone, params?: Record<string, unknown>): void {
    this._notice.set({ message: this.translate.instant(key, params), tone });
  }
}
