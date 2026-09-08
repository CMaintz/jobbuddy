package com.autoapplicant.adapter.web.dto.user;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record InterviewStoryRequest(
        UUID id,
        @NotBlank String title,
        String situation,
        String task,
        String action,
        String result,
        String reflection,
        List<String> tags
) {}
