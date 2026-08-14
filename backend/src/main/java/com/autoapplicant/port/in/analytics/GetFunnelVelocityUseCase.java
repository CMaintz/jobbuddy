package com.autoapplicant.port.in.analytics;

import com.autoapplicant.domain.analytics.FunnelVelocity;

import java.util.UUID;

public interface GetFunnelVelocityUseCase {
    FunnelVelocity getFunnelVelocity(UUID userId);
}
