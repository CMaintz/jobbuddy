package com.autoapplicant.domain.ai;

/** What one kind of generation — a cover letter, a tailored CV — has cost so far. */
public record AiOperationUsage(String operation, int tokensIn, int tokensOut, int requests) {

    public int tokens() {
        return tokensIn + tokensOut;
    }
}
