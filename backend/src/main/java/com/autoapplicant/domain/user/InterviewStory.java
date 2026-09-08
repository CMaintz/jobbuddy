package com.autoapplicant.domain.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A reusable interview story in STAR+R form. The "+R" (Reflection) is the junior-vs-senior
 * differentiator — what the candidate learned or would do differently. Same trust level as the
 * CV: the candidate's own material, drawn on for interview prep and ATS form answers.
 */
public record InterviewStory(
        UUID id,
        UUID userId,
        String title,
        String situation,
        String task,
        String action,
        String result,
        String reflection,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
