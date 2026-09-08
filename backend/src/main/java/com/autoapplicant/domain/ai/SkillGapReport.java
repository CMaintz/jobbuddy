package com.autoapplicant.domain.ai;

import java.util.List;

/**
 * Result of comparing the master profile against the jobs the user is pursuing
 * (pipeline) plus embedding-matched market jobs.
 */
public record SkillGapReport(
        List<SkillGap> gaps,
        String summary,
        /** How many job postings the comparison covered. */
        int jobsAnalyzed
) {
    public record SkillGap(
            String skill,
            /** Roughly how many of the analyzed postings ask for it. */
            int demand,
            /** HIGH | MEDIUM | LOW learning priority. */
            String priority,
            String why,
            List<String> resources
    ) {}
}
