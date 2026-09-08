package com.autoapplicant.port.out.security;

/**
 * Reversible encryption for secrets the server must be able to use again — a
 * user's API key, not a password. Passwords are Firebase's problem and are hashed,
 * never encrypted.
 */
public interface SecretCipherPort {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
    /** False when no encryption key is configured, in which case secrets must not be stored. */
    boolean isConfigured();
}
