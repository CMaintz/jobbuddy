package com.autoapplicant.adapter.security;

import com.autoapplicant.port.out.security.SecretCipherPort;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-GCM over a base64 key from configuration. GCM is authenticated, so a tampered
 * ciphertext fails to decrypt rather than yielding garbage that gets sent to a
 * provider as if it were a key.
 *
 * <p>With no key configured this reports {@link #isConfigured()} false and refuses to
 * encrypt. Storing user API keys in the clear because a deployment forgot to set a
 * secret is worse than the feature being unavailable.
 */
@Component
// final: the constructor validates and can throw, so sealing the class prevents
// a finalizer-attack subclass from capturing a partially-constructed instance
// (SpotBugs CT_CONSTRUCTOR_THROW).
public final class AesGcmSecretCipher implements SecretCipherPort {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(@Value("${app.security.secret-encryption-key:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    private static SecretKey parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.secret-encryption-key is not valid base64", e);
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "app.security.secret-encryption-key must decode to 16, 24 or 32 bytes, got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    @Override
    public boolean isConfigured() {
        return key != null;
    }

    @Override
    public String encrypt(String plaintext) {
        requireKey();
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt secret", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        requireKey();
        if (ciphertext == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt secret", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "No app.security.secret-encryption-key configured — refusing to handle stored secrets.");
        }
    }
}
