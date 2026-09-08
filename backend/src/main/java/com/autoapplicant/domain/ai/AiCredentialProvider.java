package com.autoapplicant.domain.ai;

/**
 * A provider a user can supply their own API key for. The local CLI agent is not
 * here on purpose: it is a property of the machine the server runs on, not
 * something a hosted user can bring.
 */
public enum AiCredentialProvider {
    OPENAI,
    GEMINI;

    public static AiCredentialProvider parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
