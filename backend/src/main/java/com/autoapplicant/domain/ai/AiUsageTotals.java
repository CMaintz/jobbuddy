package com.autoapplicant.domain.ai;

/** Token and request counts over some window. */
public record AiUsageTotals(int tokensIn, int tokensOut, int requests) {

    public static final AiUsageTotals NONE = new AiUsageTotals(0, 0, 0);

    public int tokens() {
        return tokensIn + tokensOut;
    }
}
