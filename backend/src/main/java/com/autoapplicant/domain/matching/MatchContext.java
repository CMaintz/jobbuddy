package com.autoapplicant.domain.matching;

import com.autoapplicant.domain.user.UserPreferences;

import java.util.List;
import java.util.UUID;

public record MatchContext(
        UUID userId,
        float[] userEmbedding,
        List<UUID> savedJobIds,
        List<UUID> ignoredJobIds,
        List<UUID> appliedJobIds,
        UserPreferences preferences
) {}
