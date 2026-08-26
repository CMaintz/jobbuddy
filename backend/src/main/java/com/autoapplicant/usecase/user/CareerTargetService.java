package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.CareerStage;
import com.autoapplicant.domain.user.CareerTarget;
import com.autoapplicant.port.in.user.ManageCareerTargetUseCase;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CareerTargetService implements ManageCareerTargetUseCase {

    private final CareerTargetRepositoryPort repo;

    public CareerTargetService(CareerTargetRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public CareerTarget get(UUID userId) {
        return repo.findByUserId(userId)
                .orElseGet(() -> new CareerTarget(userId, List.of(), null, null, List.of(), null, null));
    }

    @Override
    public CareerTarget upsert(UUID userId, List<String> targetArchetypes, String northStar,
                               String narrative, List<String> cultureRequirements, CareerStage careerStage) {
        return repo.save(new CareerTarget(userId,
                targetArchetypes != null ? targetArchetypes : List.of(),
                northStar, narrative,
                cultureRequirements != null ? cultureRequirements : List.of(),
                careerStage,
                Instant.now()));
    }
}
