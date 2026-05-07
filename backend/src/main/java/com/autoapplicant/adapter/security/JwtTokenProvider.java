package com.autoapplicant.adapter.security;

import com.autoapplicant.config.AppProperties;
import com.autoapplicant.domain.user.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final AppProperties props;
    private SecretKey signingKey;

    public JwtTokenProvider(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UUID userId, String email, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + props.getJwt().getExpirationMs());
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseToken(token).getSubject());
    }

    public String extractRole(String token) {
        return (String) parseToken(token).get("role");
    }
}
