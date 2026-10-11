package com.autoapplicant.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.port.in.auth.LinkedInIdentity;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkedInUserInfoTest {

  @Test
  void reads_a_verified_email() {
    LinkedInIdentity identity = LinkedInUserInfo.toIdentity(claims(true));

    assertThat(identity.sub()).isEqualTo("sub-1");
    assertThat(identity.email()).isEqualTo("member@example.com");
    assertThat(identity.emailVerified()).isTrue();
    assertThat(identity.fullName()).isEqualTo("Ada Lovelace");
  }

  @Test
  void treats_an_explicitly_unverified_email_as_unverified() {
    assertThat(LinkedInUserInfo.toIdentity(claims(false)).emailVerified()).isFalse();
  }

  @Test
  void fails_closed_when_the_claim_is_missing() {
    Map<String, Object> claims = claims(true);
    claims.remove("email_verified");

    assertThat(LinkedInUserInfo.toIdentity(claims).emailVerified()).isFalse();
  }

  @Test
  void fails_closed_when_the_claim_is_not_a_boolean() {
    Map<String, Object> claims = claims(true);
    claims.put("email_verified", "true");

    assertThat(LinkedInUserInfo.toIdentity(claims).emailVerified()).isFalse();
  }

  @Test
  void builds_the_name_from_its_parts_when_no_full_name_is_given() {
    Map<String, Object> claims = claims(true);
    claims.remove("name");

    assertThat(LinkedInUserInfo.toIdentity(claims).fullName()).isEqualTo("Ada Lovelace");
  }

  private Map<String, Object> claims(boolean emailVerified) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "sub-1");
    claims.put("email", "member@example.com");
    claims.put("email_verified", emailVerified);
    claims.put("name", "Ada Lovelace");
    claims.put("given_name", "Ada");
    claims.put("family_name", "Lovelace");
    return claims;
  }
}
