package com.autoapplicant.domain.document;

import com.autoapplicant.domain.job.JobRequirement;

import java.util.List;

/**
 * The posting as generation sees it: its text, the market it hires into, and the person it names.
 *
 * <p>These three travel together through every generation prompt, and passing them separately was
 * pushing the compose methods past a dozen parameters. Any of them may be null — a pasted job
 * description has no country and names nobody.
 *
 * @param description   the cleaned posting text, or null when generating without one
 * @param country       the posting's country, which selects market conventions
 * @param contactPerson the person the posting names, pre-rendered ("Mette Hansen, afdelingsleder"),
 *                      or null. Only ever a name the posting itself stated — never inferred.
 * @param requirements  everything the posting asks for, in its own words — including the asks that
 *                      are not short skill labels, which the tiered skill lists drop by design.
 */
public record PostingContext(String description, String country, String contactPerson,
                             List<JobRequirement> requirements) {

    public PostingContext {
        requirements = requirements == null ? List.of() : requirements;
    }

    public PostingContext(String description, String country, String contactPerson) {
        this(description, country, contactPerson, List.of());
    }

    public static final PostingContext EMPTY = new PostingContext(null, null, null, List.of());

    /** A posting with text but no known country or contact — the pasted-description case. */
    public static PostingContext ofDescription(String description) {
        return new PostingContext(description, null, null);
    }

    /** The asks the posting demands, ahead of the ones it merely likes. */
    public List<JobRequirement> requiredFirst() {
        return java.util.stream.Stream.concat(
                requirements.stream().filter(JobRequirement::isRequired),
                requirements.stream().filter(r -> !r.isRequired())).toList();
    }

    public boolean hasContactPerson() {
        return contactPerson != null && !contactPerson.isBlank();
    }
}
