package com.autoapplicant.domain.ai;

import java.util.List;

public record ReviewDocumentResult(
        String revisedContent,
        /** What the reviewer changed or flagged — shown alongside the diff. */
        List<String> critique,
        String modelUsed
) {}
