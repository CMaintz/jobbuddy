package com.autoapplicant.domain.ai;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's own API key for generation. The key travels in the clear only between
 * the request that sets it and the cipher; it is stored encrypted and never
 * returned to a client.
 */
public record AiCredential(
        UUID userId,
        AiCredentialProvider provider,
        String apiKey,
        String model,
        Instant updatedAt
) {
    /** The last four characters, for telling the user which key is stored. */
    public String hint() {
        if (apiKey == null || apiKey.length() < 4) return "••••";
        return "••••" + apiKey.substring(apiKey.length() - 4);
    }
}
