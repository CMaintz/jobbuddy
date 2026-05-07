package com.autoapplicant.adapter.web.dto.document;

import jakarta.validation.constraints.NotBlank;

public record CvUploadRequest(
        @NotBlank String name,
        @NotBlank String content,
        String format
) {}
