import { OAuthState } from './oauth-state';

describe('OAuthState', () => {
  const KEY = 'test_oauth_state';
  let state: OAuthState;

  beforeEach(() => {
    sessionStorage.removeItem(KEY);
    state = new OAuthState(KEY);
  });

  it('accepts the state it issued', () => {
    const issued = state.issue();

    expect(state.consume(issued)).toBeTrue();
  });

  it('issues a different value each time', () => {
    expect(state.issue()).not.toEqual(state.issue());
  });

  it('rejects a mismatched state', () => {
    state.issue();

    expect(state.consume('forged')).toBeFalse();
  });

  it('rejects a missing state', () => {
    state.issue();

    expect(state.consume(null)).toBeFalse();
  });

  it('rejects a callback when no flow was started in this tab', () => {
    expect(state.consume('anything')).toBeFalse();
  });

  it('cannot be replayed', () => {
    const issued = state.issue();
    state.consume(issued);

    expect(state.consume(issued)).toBeFalse();
  });
});
