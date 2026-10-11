/** The part of a Firebase user the verification gate looks at. */
export interface VerifiableUser {
  emailVerified: boolean;
}

/**
 * The backend rejects any token without a verified email, so a signed-in user without one has
 * nothing to do in the app yet. That includes LinkedIn custom-token sessions: the backend gives
 * their Firebase account the LinkedIn-verified address before minting the token.
 */
export function requiresEmailVerification(user: VerifiableUser | null): boolean {
  return !!user && !user.emailVerified;
}
