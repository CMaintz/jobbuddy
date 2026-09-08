package com.autoapplicant.adapter.web.dto.user;

import jakarta.validation.constraints.NotBlank;

public record RetractedClaimRequest(
        @NotBlank String claim,
        String reason
) {}
