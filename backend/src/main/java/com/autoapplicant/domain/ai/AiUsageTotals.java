package com.autoapplicant.domain.ai;

/** Token and request counts over some window. */
public record AiUsageTotals(long tokensIn, long tokensOut, long requests) {

    public static final AiUsageTotals NONE = new AiUsageTotals(0, 0, 0);

    public long tokens() {
        return tokensIn + tokensOut;
    }
}
