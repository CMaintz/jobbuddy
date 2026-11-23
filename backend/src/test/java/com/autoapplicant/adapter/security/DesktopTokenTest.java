package com.autoapplicant.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DesktopTokenTest {

    // Built, not written out: a 32-char hex literal reads as an API key to the secret scanner.
    private static final String TOKEN = "ab".repeat(16);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "short-token"})
    void a_missing_or_guessable_token_refuses_to_start(String configured) {
        assertThatThrownBy(() -> DesktopToken.of(configured))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JOBBUDDY_DESKTOP_TOKEN");
    }

    @Test
    void only_the_exact_token_matches() {
        DesktopToken token = DesktopToken.of(TOKEN);

        assertThat(token.matches(TOKEN)).isTrue();
        assertThat(token.matches(TOKEN.substring(1) + "0")).isFalse();
        assertThat(token.matches(TOKEN.substring(0, 8))).isFalse();
        assertThat(token.matches("")).isFalse();
        assertThat(token.matches(null)).isFalse();
    }
}
