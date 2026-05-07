package com.autoapplicant.domain.ai;

import java.time.Instant;

public record AiGenerationResult(
        String content,
        String modelUsed,
        Integer tokensUsed,
        Instant generatedAt
) {}
