package com.autoapplicant.domain.company;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A company worth an unsolicited application ("uopfordret ansøgning"), with the reasons it was
 * picked.
 *
 * <p>Around half of Danish vacancies are never advertised — 62% in private companies — and roughly
 * a quarter of those hires come through unsolicited contact. The app could already write the
 * letter; this is the missing half, which is knowing who to send it to.
 *
 * @param score   0–100, comparable only within one result set
 * @param reasons why it was picked, as translatable codes — so the ranking is arguable rather
 *                than magic, and arguable in the reader's own language
 */
public record OutreachTarget(
        UUID companyId,
        String companyName,
        String website,
        int score,
        List<OutreachReason> reasons,
        Instant lastPostedAt,
        List<String> matchedTechnologies,
        boolean hasOpenRole) {}
