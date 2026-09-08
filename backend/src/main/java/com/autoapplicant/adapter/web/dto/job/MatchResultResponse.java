package com.autoapplicant.adapter.web.dto.job;

import com.autoapplicant.domain.matching.MatchResult;

import java.util.List;
import java.util.UUID;

/**
 * A recommendation as the feed reads it: the score, the reasons behind it, and the
 * posting in preview form. The full description is one request away on the job
 * itself, so a page of recommendations stays small.
 */
public record MatchResultResponse(
        UUID jobId,
        int totalScore,
        String matchLabel,
        List<String> matchReasons,
        boolean hardConstraintPassed,
        JobResponse job
) {
    public static MatchResultResponse from(MatchResult result) {
        return new MatchResultResponse(
                result.jobId(),
                result.totalScore(),
                result.matchLabel() != null ? result.matchLabel().name() : null,
                result.matchReasons(),
                result.hardConstraintPassed(),
                JobResponse.preview(result.job()));
    }
}
