import { requiresEmailVerification } from './email-verification';

describe('requiresEmailVerification', () => {
  it('lets a verified password or Google user through', () => {
    expect(requiresEmailVerification({ emailVerified: true })).toBe(false);
  });

  it('holds back a user whose email is unverified', () => {
    expect(requiresEmailVerification({ emailVerified: false })).toBe(true);
  });

  it('lets a LinkedIn custom-token session through once its account carries the verified email', () => {
    const linkedInSession = { emailVerified: true, providerData: [] };

    expect(requiresEmailVerification(linkedInSession)).toBe(false);
  });

  it('holds back a legacy custom-token session with no verified email on its account', () => {
    const legacySession = { emailVerified: false, providerData: [] };

    expect(requiresEmailVerification(legacySession)).toBe(true);
  });

  it('has nothing to gate without a session', () => {
    expect(requiresEmailVerification(null)).toBe(false);
  });
});
