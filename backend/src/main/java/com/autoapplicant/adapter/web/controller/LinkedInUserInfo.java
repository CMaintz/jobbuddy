package com.autoapplicant.adapter.web.controller;

import com.autoapplicant.port.in.auth.LinkedInIdentity;
import java.util.Map;

/** Maps LinkedIn's OIDC userinfo claims onto a {@link LinkedInIdentity}. */
final class LinkedInUserInfo {

  private LinkedInUserInfo() {}

  /**
   * Only a literal {@code true} counts as verified: a missing or malformed {@code email_verified}
   * claim must fail closed, or an unverified address could be linked to someone else's account.
   */
  static LinkedInIdentity toIdentity(Map<String, Object> claims) {
    String sub = (String) claims.get("sub");
    String email = (String) claims.get("email");
    boolean emailVerified = Boolean.TRUE.equals(claims.get("email_verified"));
    return new LinkedInIdentity(sub, email, emailVerified, fullName(claims));
  }

  private static String fullName(Map<String, Object> claims) {
    String givenName = (String) claims.getOrDefault("given_name", "");
    String familyName = (String) claims.getOrDefault("family_name", "");
    return (String) claims.getOrDefault("name", (givenName + " " + familyName).trim());
  }
}
