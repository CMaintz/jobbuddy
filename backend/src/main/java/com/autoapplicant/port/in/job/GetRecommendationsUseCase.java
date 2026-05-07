package com.autoapplicant.port.in.job;

import com.autoapplicant.domain.matching.MatchResult;

import java.util.List;
import java.util.UUID;

public interface GetRecommendationsUseCase {
    List<MatchResult> getRecommendations(UUID userId, int limit);
}
