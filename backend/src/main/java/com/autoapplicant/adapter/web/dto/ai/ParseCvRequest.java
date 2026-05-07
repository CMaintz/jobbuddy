package com.autoapplicant.adapter.web.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record ParseCvRequest(
        @NotBlank String rawCvText
) {}
