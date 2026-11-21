package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.chat.ChatAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The tokens that open the MCP endpoint, one per running chat turn. A token is not the desktop
 * shell's: it reaches the {@code claude} child (in its environment), so it is worth nothing once
 * the turn ends and opens nothing but {@code /mcp}. Tokens live in memory only; a restart
 * forgets them with the turns they belonged to.
 */
@Component
@Profile("desktop")
public class McpGrants {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** Keyed by the token's digest, so the map's own lookups do not compare the token itself. */
    private final Map<String, McpGrant> grants = new ConcurrentHashMap<>();

    public String issue(UUID userId, Consumer<ChatAction> actions) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        grants.put(digest(token), new McpGrant(userId, actions));
        return token;
    }

    public Optional<McpGrant> find(String token) {
        return token == null || token.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(grants.get(digest(token)));
    }

    public void revoke(String token) {
        grants.remove(digest(token));
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform provides SHA-256", e);
        }
    }
}
