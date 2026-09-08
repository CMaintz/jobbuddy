package com.autoapplicant.domain.user;

import com.autoapplicant.domain.job.EmploymentType;
import com.autoapplicant.domain.job.RemoteType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The user's profile.
 *
 * <p>Skills are deliberately not here. They live in {@code profile_skills}, one row each, carrying
 * taxonomy link, category, proficiency and years. They used to ALSO exist as {@code skills} and
 * {@code technologies} text[] columns, written by the CV and LinkedIn importers while manual entry
 * wrote rows — so whether a skill had a category depended on how it had arrived, and for anyone
 * who onboarded by uploading a CV the answer was "it did not". One representation now, whatever
 * the source. The skills/technologies distinction is derived from the taxonomy category instead of
 * stored twice (see {@code SkillCategories}).
 */
public record Profile(
        UUID id,
        UUID userId,
        String headline,
        String summary,
        Integer yearsExperience,
        List<String> languages,
        /** Leisure interests — the closing section of a Danish CV. Empty when the user gave none. */
        List<String> interests,
        Integer desiredSalaryMin,
        Integer desiredSalaryMax,
        String desiredCurrency,
        RemoteType remotePreference,
        EmploymentType employmentTypePreference,
        Instant createdAt,
        Instant updatedAt
) {}
