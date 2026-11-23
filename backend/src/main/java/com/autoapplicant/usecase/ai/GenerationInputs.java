package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.usecase.document.EmployerPosting;

/**
 * What the prompt-assembly phase resolves and the later phases need: the composed prompt plus the
 * bits of context (the posting, the contact-free profile JSON, the resolved job description) that the
 * guard and quality-scoring phases would otherwise have to re-derive.
 */
public record GenerationInputs(
        PromptComposition composition, Job job, String contactFreeJson, String jobDescription) {

    /** The posting the document was written from, with the company's name when the job is known. */
    public EmployerPosting employerPosting() {
        return new EmployerPosting(jobDescription, job != null ? job.companyName() : null);
    }
}
