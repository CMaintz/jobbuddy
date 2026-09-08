package com.autoapplicant.adapter.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String API_KEY = "sk-proj-abcdefghijklmnopqrstuvwxyz0123456789";

    @Test
    void a_key_survives_the_round_trip() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);
        assertThat(cipher.decrypt(cipher.encrypt(API_KEY))).isEqualTo(API_KEY);
    }

    @Test
    void the_same_key_encrypts_differently_every_time() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);
        // A fresh IV per call, so two users with the same key do not produce identical rows.
        assertThat(cipher.encrypt(API_KEY)).isNotEqualTo(cipher.encrypt(API_KEY));
    }

    @Test
    void the_stored_form_does_not_contain_the_key() {
        assertThat(new AesGcmSecretCipher(KEY).encrypt(API_KEY)).doesNotContain(API_KEY, "sk-proj");
    }

    @Test
    void a_tampered_ciphertext_fails_rather_than_yielding_garbage() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);
        String stored = cipher.encrypt(API_KEY);
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A=") ? "B=" : "A=");
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_key_from_another_deployment_cannot_read_it() {
        byte[] other = new byte[32];
        other[0] = 7;
        String stored = new AesGcmSecretCipher(KEY).encrypt(API_KEY);
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(other)).decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void with_no_secret_configured_it_reports_unavailable_and_refuses_to_encrypt() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher("");
        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt(API_KEY))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refusing");
    }

    @Test
    void a_secret_of_the_wrong_length_fails_at_startup_not_at_first_use() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(Base64.getEncoder().encodeToString(new byte[7])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("16, 24 or 32 bytes");
    }
}
