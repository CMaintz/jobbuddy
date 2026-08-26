package com.autoapplicant.usecase.user;

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
                .orElseGet(() -> new CareerTarget(userId, List.of(), null, null, List.of(), null,
                        null, null, null));
    }

    @Override
    public CareerTarget upsert(UUID userId, CareerTarget draft) {
        return repo.save(new CareerTarget(userId,
                draft.targetArchetypes() != null ? draft.targetArchetypes() : List.of(),
                draft.northStar(), draft.narrative(),
                draft.cultureRequirements() != null ? draft.cultureRequirements() : List.of(),
                draft.careerStage(),
                blankToNull(draft.noticePeriod()),
                draft.earliestStartDate(),
                Instant.now()));
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.strip() : null;
    }
}
