package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.job.Job;

/**
 * What the prompt-assembly phase resolves and the later phases need: the composed prompt plus the
 * bits of context (the posting, the contact-free profile JSON, the resolved job description) that the
 * guard and quality-scoring phases would otherwise have to re-derive.
 */
public record GenerationInputs(
        PromptComposition composition, Job job, String contactFreeJson, String jobDescription) {}
