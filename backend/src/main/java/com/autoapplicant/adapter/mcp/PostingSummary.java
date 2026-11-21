package com.autoapplicant.adapter.mcp;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.MatchResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * A posting in a tool's list: enough to choose by, short enough that ten fit in a few hundred
 * tokens. Title and company are the employer's words, so they are data like the posting text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record PostingSummary(UUID id, String title, String company, String location, String category,
                      String posted, String deadline, Integer matchScore, String matchLabel) {

    static final int TITLE_CHARS = 120;

    /** A title, company or place, the employer's words, cut to a length worth its tokens. */
    static String cut(String name) {
        return McpText.cut(name, TITLE_CHARS);
    }

    static PostingSummary of(Job job, MatchResult match) {
        return new PostingSummary(job.id(), cut(job.title()), cut(job.companyName()),
                cut(job.location()),
                job.jobCategory() == null ? null : job.jobCategory().name(),
                job.postedAt() == null ? null : job.postedAt().atZone(ZoneOffset.UTC).toLocalDate()
                        .toString(),
                job.applicationDeadline() == null ? null : job.applicationDeadline().toString(),
                match == null ? null : match.totalScore(),
                match == null || match.matchLabel() == null ? null : match.matchLabel().name());
    }

    static PostingSummary of(Job job) {
        return of(job, null);
    }
}
