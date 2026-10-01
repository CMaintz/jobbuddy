package com.autoapplicant.domain.ai;

/** What one kind of generation — a cover letter, a tailored CV — has cost so far. */
public record AiOperationUsage(String operation, long tokensIn, long tokensOut, long requests) {

    public long tokens() {
        return tokensIn + tokensOut;
    }
}
