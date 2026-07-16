package com.autoapplicant.adapter.web.dto.application;

public record UpdateOutcomeRequest(
        String outcomeFeedback,
        String outcomeLessons
) {}
