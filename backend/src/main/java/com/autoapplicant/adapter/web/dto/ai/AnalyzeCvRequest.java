package com.autoapplicant.adapter.web.dto.ai;

import java.util.UUID;

/**
 * All fields optional: with no cvVersionId the master profile is analyzed;
 * with neither jobId nor jobDescription the CV is judged on general strength.
 */
public record AnalyzeCvRequest(
        UUID cvVersionId,
        UUID jobId,
        String jobDescription
) {}
