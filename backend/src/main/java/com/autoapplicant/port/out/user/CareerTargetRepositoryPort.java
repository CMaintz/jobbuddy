package com.autoapplicant.port.out.user;

import com.autoapplicant.domain.user.CareerTarget;

import java.util.Optional;
import java.util.UUID;

public interface CareerTargetRepositoryPort {
    Optional<CareerTarget> findByUserId(UUID userId);
    CareerTarget save(CareerTarget target);
}
