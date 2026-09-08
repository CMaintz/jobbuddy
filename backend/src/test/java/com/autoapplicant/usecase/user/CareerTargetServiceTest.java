package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.CareerStage;
import com.autoapplicant.domain.user.CareerTarget;
import com.autoapplicant.port.out.user.CareerTargetRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CareerTargetServiceTest {

    private static final UUID USER = UUID.randomUUID();

    /** Captures whatever the service hands the repository. */
    private static class CapturingRepo implements CareerTargetRepositoryPort {
        CareerTarget saved;
        Optional<CareerTarget> stored = Optional.empty();

        @Override public Optional<CareerTarget> findByUserId(UUID userId) { return stored; }
        @Override public CareerTarget save(CareerTarget target) { saved = target; return target; }
    }

    private final CapturingRepo repo = new CapturingRepo();
    private final CareerTargetService service = new CareerTargetService(repo);

    private static CareerTarget draft(String noticePeriod, LocalDate start) {
        return new CareerTarget(null, List.of("Backend"), "north", "narrative", List.of(),
                CareerStage.NEW_GRAD, noticePeriod, start, null);
    }

    @Test
    void availabilityIsPersisted() {
        service.upsert(USER, draft("3 måneder", LocalDate.of(2026, 9, 1)));
        assertThat(repo.saved.noticePeriod()).isEqualTo("3 måneder");
        assertThat(repo.saved.earliestStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void blankNoticePeriodIsStoredAsUnset() {
        service.upsert(USER, draft("   ", null));
        assertThat(repo.saved.noticePeriod()).isNull();
        assertThat(repo.saved.earliestStartDate()).isNull();
    }

    @Test
    void callerIdentityAndClockWinOverTheDraft() {
        UUID spoofed = UUID.randomUUID();
        service.upsert(USER, new CareerTarget(spoofed, List.of(), null, null, List.of(), null,
                null, null, Instant.EPOCH));
        assertThat(repo.saved.userId()).isEqualTo(USER);
        assertThat(repo.saved.updatedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void emptyDefaultIsReturnedWhenNoTargetExists() {
        CareerTarget target = service.get(USER);
        assertThat(target.userId()).isEqualTo(USER);
        assertThat(target.noticePeriod()).isNull();
        assertThat(target.targetArchetypes()).isEmpty();
    }
}
