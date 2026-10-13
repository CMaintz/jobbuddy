package com.autoapplicant.adapter.web.dto.ai;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public record RefineRequest(
        @NotBlank String currentContent,
        @NotBlank String userMessage,
        String jobDescription,
        String targetLanguage,
        /** The CV section being refined (e.g. "profile", "experience"); optional. */
        String sectionKey,
        /** A section template for this refine (e.g. STAR); optional, needs sectionKey. */
        UUID promptTemplateId
) {}
