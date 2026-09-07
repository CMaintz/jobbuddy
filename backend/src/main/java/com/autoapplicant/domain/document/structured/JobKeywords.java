package com.autoapplicant.domain.document.structured;

import com.autoapplicant.domain.job.Job;

import java.util.List;

/**
 * What a posting actually asks for, as the enrichment pass tiered it. These are the
 * keywords a document is measured against — taken from the posting rather than
 * nominated by the model writing the document, so the measurement is independent of
 * the thing being measured.
 */
public record JobKeywords(List<String> required, List<String> preferred) {

    public static final JobKeywords NONE = new JobKeywords(List.of(), List.of());

    /**
     * The posting's asks as enrichment tiered them. An unenriched job yields nothing to
     * measure against, which the report shows as "not measured" rather than as zero.
     */
    public static JobKeywords of(Job job) {
        if (job == null) return NONE;
        return new JobKeywords(job.requiredSkills(), job.preferredSkills());
    }

    public JobKeywords {
        required = required == null ? List.of() : required;
        preferred = preferred == null ? List.of() : preferred;
    }

    public boolean isEmpty() {
        return required.isEmpty() && preferred.isEmpty();
    }
}
