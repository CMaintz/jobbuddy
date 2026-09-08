package com.autoapplicant.adapter.web.dto.user;

import com.autoapplicant.domain.user.CareerStage;

import java.util.List;

public record CareerTargetRequest(
        List<String> targetArchetypes,
        String northStar,
        String narrative,
        List<String> cultureRequirements,
        CareerStage careerStage
) {}
