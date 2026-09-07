package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;

import java.util.List;

/**
 * What a posting actually asks for, as the enrichment pass extracted it. The two keyword
 * lists are what a document is measured against — taken from the posting rather than
 * nominated by the model writing the document, so the measurement is independent of the
 * thing being measured.
 *
 * <p>{@code requirements} is the unnarrowed picture: every ask in the posting's own words,
 * including the ones that are not short skill labels ("5 års erfaring med backend",
 * "kørekort B"). Those are never folded into the keyword percentage — a number that counts
 * "5 years of experience" as matched because the CV says "experience" would look measured
 * and be nothing of the sort. They are listed for the reader instead.
 *
 * @param required     skill labels the posting demands
 * @param preferred    skill labels the posting merely likes
 * @param requirements every ask, tiered and kinded, in the posting's own words
 */
public record JobKeywords(List<String> required, List<String> preferred,
                          List<JobRequirement> requirements) {

    public static final JobKeywords NONE = new JobKeywords(List.of(), List.of(), List.of());

    /**
     * The posting's asks as enrichment tiered them. An unenriched job yields nothing to
     * measure against, which the report shows as "not measured" rather than as zero.
     */
    public static JobKeywords of(Job job) {
        if (job == null) return NONE;
        return new JobKeywords(job.requiredSkills(), job.preferredSkills(), job.requirements());
    }

    public JobKeywords {
        required = required == null ? List.of() : required;
        preferred = preferred == null ? List.of() : preferred;
        requirements = requirements == null ? List.of() : requirements;
    }

    public JobKeywords(List<String> required, List<String> preferred) {
        this(required, preferred, List.of());
    }

    public boolean isEmpty() {
        return required.isEmpty() && preferred.isEmpty();
    }

    /**
     * The asks that no keyword check can settle — experience quantities, education, licences.
     * Skill asks are left out: they are already counted, and listing them twice reads as two
     * findings about one thing.
     */
    public List<JobRequirement> unmeasurableRequirements() {
        return requirements.stream().filter(r -> !r.kind().isKeywordCheckable()).toList();
    }
}
