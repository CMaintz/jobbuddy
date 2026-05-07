package com.autoapplicant.domain.matching;

import com.autoapplicant.domain.job.Job;

import java.util.UUID;

public record MatchResult(
        UUID jobId,
        UUID userId,
        Job job,
        boolean hardConstraintPassed,
        double semanticScore,
        double behavioralScore,
        int totalScore,
        MatchLabel matchLabel
) {}
