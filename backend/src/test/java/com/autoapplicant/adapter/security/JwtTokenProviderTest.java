package com.autoapplicant.adapter.security;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String VALID_SECRET = "this-is-a-test-secret-at-least-32-chars-long";
    private static final long EXPIRY_MS = 3_600_000L; // 1 hour

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(VALID_SECRET);
        props.getJwt().setExpirationMs(EXPIRY_MS);
        provider = new JwtTokenProvider(props);
        provider.init();
    }

    // ── generateToken / parseToken round-trip ────────────────────────────────

    @Test
    void generated_token_is_not_blank() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "alice@example.com", UserRole.USER);
        assertThat(token).isNotBlank();
    }

    @Test
    void parse_token_extracts_user_id_as_subject() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "alice@example.com", UserRole.USER);
        Claims claims = provider.parseToken(token);
        assertThat(UUID.fromString(claims.getSubject())).isEqualTo(userId);
    }

    @Test
    void parse_token_extracts_email_claim() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "alice@example.com", UserRole.USER);
        assertThat(provider.parseToken(token).get("email")).isEqualTo("alice@example.com");
    }

    @Test
    void parse_token_extracts_role_claim() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "admin@example.com", UserRole.ADMIN);
        assertThat(provider.parseToken(token).get("role")).isEqualTo("ADMIN");
    }

    // ── Convenience extractors ────────────────────────────────────────────────

    @Test
    void extract_user_id_returns_correct_uuid() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "bob@example.com", UserRole.USER);
        assertThat(provider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void extract_role_returns_correct_role_string() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateToken(userId, "admin@example.com", UserRole.ADMIN);
        assertThat(provider.extractRole(token)).isEqualTo("ADMIN");
    }

    // ── validateToken ─────────────────────────────────────────────────────────

    @Test
    void validate_token_returns_true_for_valid_token() {
        String token = provider.generateToken(UUID.randomUUID(), "ok@example.com", UserRole.USER);
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validate_token_returns_false_for_garbage() {
        assertThat(provider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void validate_token_returns_false_for_wrong_signature() {
        // Build a token with a different provider (different secret)
        AppProperties otherProps = new AppProperties();
        otherProps.getJwt().setSecret("completely-different-secret-at-least-32-chars");
        otherProps.getJwt().setExpirationMs(EXPIRY_MS);
        JwtTokenProvider other = new JwtTokenProvider(otherProps);
        other.init();

        String foreignToken = other.generateToken(UUID.randomUUID(), "x@example.com", UserRole.USER);
        assertThat(provider.validateToken(foreignToken)).isFalse();
    }

    @Test
    void validate_token_returns_false_for_expired_token() throws InterruptedException {
        AppProperties shortProps = new AppProperties();
        shortProps.getJwt().setSecret(VALID_SECRET);
        shortProps.getJwt().setExpirationMs(1L); // 1 ms — expires immediately
        JwtTokenProvider shortLived = new JwtTokenProvider(shortProps);
        shortLived.init();

        String token = shortLived.generateToken(UUID.randomUUID(), "x@example.com", UserRole.USER);
        Thread.sleep(10);
        assertThat(shortLived.validateToken(token)).isFalse();
    }

    // ── @PostConstruct validation ─────────────────────────────────────────────

    @Test
    void init_throws_when_secret_is_null() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret(null);
        JwtTokenProvider p = new JwtTokenProvider(props);
        assertThatThrownBy(p::init).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    void init_throws_when_secret_is_too_short() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("short");
        JwtTokenProvider p = new JwtTokenProvider(props);
        assertThatThrownBy(p::init).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 characters");
    }

    @Test
    void init_accepts_exactly_32_char_secret() {
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("exactly-32-characters-right-here"); // 32 chars
        props.getJwt().setExpirationMs(EXPIRY_MS);
        JwtTokenProvider p = new JwtTokenProvider(props);
        p.init(); // must not throw
        assertThat(p.validateToken(
                p.generateToken(UUID.randomUUID(), "x@example.com", UserRole.USER)
        )).isTrue();
    }
}
