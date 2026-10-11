package com.autoapplicant.port.in.auth;

/**
 * The identity LinkedIn's OIDC userinfo endpoint asserts for a sign-in.
 *
 * @param sub LinkedIn's stable member id
 * @param email the member's primary email; may be null
 * @param emailVerified whether LinkedIn vouches for {@code email}; false when the claim is absent
 * @param fullName display name; may be blank
 */
public record LinkedInIdentity(String sub, String email, boolean emailVerified, String fullName) {}
