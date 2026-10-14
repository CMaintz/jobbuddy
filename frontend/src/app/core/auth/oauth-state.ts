const STATE_BYTES = 16;

/**
 * One-time CSRF token for an OAuth redirect round trip (RFC 6749 section 10.12). The value lives in
 * sessionStorage so only the tab that started the flow can finish it.
 */
export class OAuthState {
  constructor(private readonly storageKey: string, private readonly storage: Storage = sessionStorage) {}

  /** Generates a fresh unguessable state, remembers it, and returns it for the authorize URL. */
  issue(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(STATE_BYTES));
    const state = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    this.storage.setItem(this.storageKey, state);
    return state;
  }

  /**
   * True only when `returned` matches the issued state. The stored value is removed either way,
   * so a callback URL cannot be replayed.
   */
  consume(returned: string | null): boolean {
    const expected = this.storage.getItem(this.storageKey);
    this.storage.removeItem(this.storageKey);
    return !!expected && !!returned && expected === returned;
  }
}
