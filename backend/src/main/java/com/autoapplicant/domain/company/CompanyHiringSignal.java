package com.autoapplicant.domain.company;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the crawled job history says about one company's hiring, aggregated.
 *
 * <p>The raw material for ranking unsolicited-application targets: a company that has hired people
 * like you repeatedly, but has nothing open right now, is the classic target — if it had an open
 * role you would simply apply to it.
 *
 * @param postingCount  postings seen from this company in the window
 * @param activeCount   of those, still open
 * @param lastPostedAt  most recent posting date in the window
 * @param technologies  distinct technologies across those postings
 */
public record CompanyHiringSignal(
        UUID companyId,
        String companyName,
        int postingCount,
        int activeCount,
        Instant lastPostedAt,
        List<String> technologies) {}
