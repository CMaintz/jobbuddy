package com.autoapplicant.domain.ai;

/**
 * One completion from a chat provider, with whatever the provider told us it cost.
 * Providers that do not report token counts (the local CLI agent) return zeros —
 * a zero means "not reported", not "free".
 */
public record AiCompletion(String text, int tokensIn, int tokensOut, String model) {

    public static AiCompletion untracked(String text, String model) {
        return new AiCompletion(text, 0, 0, model);
    }

    public boolean hasTokenCounts() {
        return tokensIn > 0 || tokensOut > 0;
    }
}
