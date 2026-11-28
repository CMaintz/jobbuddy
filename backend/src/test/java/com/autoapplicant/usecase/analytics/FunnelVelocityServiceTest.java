package com.autoapplicant.usecase.analytics;

import static com.autoapplicant.domain.application.ApplicationStatus.APPLIED;
import static com.autoapplicant.domain.application.ApplicationStatus.PREPARING;
import static com.autoapplicant.domain.application.ApplicationStatus.SAVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.analytics.FunnelVelocity;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.application.ApplicationStatusEvent;
import com.autoapplicant.port.out.application.ApplicationStatusEventRepositoryPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FunnelVelocityServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID APP_A = UUID.randomUUID();
    private static final UUID APP_B = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-03-02T09:00:00Z");

    private final ApplicationStatusEventRepositoryPort events = mock(ApplicationStatusEventRepositoryPort.class);
    private final FunnelVelocityService service = new FunnelVelocityService(events);

    @Test
    void an_application_ledger_read_out_of_order_is_timed_in_the_order_it_happened() {
        givenLedger(
                event(APP_A, PREPARING, APPLIED, Duration.ofDays(3)),
                event(APP_A, null, SAVED, Duration.ZERO),
                event(APP_A, SAVED, PREPARING, Duration.ofDays(2)));

        assertThat(velocity())
                .extracting(FunnelVelocity.Transition::fromStatus, FunnelVelocity.Transition::toStatus,
                        FunnelVelocity.Transition::avgDays)
                .containsExactlyInAnyOrder(
                        tuple("SAVED", "PREPARING", 2.0),
                        tuple("PREPARING", "APPLIED", 1.0));
    }

    @Test
    void the_same_move_in_two_applications_is_averaged_and_never_paired_across_them() {
        // Interleaved in time: pairing by ledger position instead of by application would time
        // A's move against B's creation.
        givenLedger(
                event(APP_A, null, SAVED, Duration.ZERO),
                event(APP_B, null, SAVED, Duration.ofDays(1)),
                event(APP_A, SAVED, PREPARING, Duration.ofDays(2)),
                event(APP_B, SAVED, PREPARING, Duration.ofDays(4)));

        assertThat(velocity()).singleElement().satisfies(t -> {
            assertThat(t.fromStatus()).isEqualTo("SAVED");
            assertThat(t.toStatus()).isEqualTo("PREPARING");
            assertThat(t.count()).isEqualTo(2);
            assertThat(t.avgDays()).isEqualTo(2.5);
        });
    }

    @Test
    void a_row_without_a_from_status_is_attributed_to_the_stage_before_it_and_rounded_to_a_tenth() {
        givenLedger(
                event(APP_A, null, SAVED, Duration.ZERO),
                event(APP_A, null, APPLIED, Duration.ofHours(8)));

        assertThat(velocity()).singleElement().satisfies(t -> {
            assertThat(t.fromStatus()).isEqualTo("SAVED");
            assertThat(t.toStatus()).isEqualTo("APPLIED");
            assertThat(t.avgDays()).isEqualTo(0.3);
        });
    }

    @Test
    void the_busiest_transition_comes_first_and_a_lone_creation_counts_for_nothing() {
        // One application keeps the insertion order fixed (the per-application grouping is a
        // hash map), so only the sort can put the twice-seen move ahead of the once-seen one.
        // The service takes the ledger as it finds it, a repeated row included.
        givenLedger(
                event(APP_A, null, SAVED, Duration.ZERO),
                event(APP_A, SAVED, PREPARING, Duration.ofDays(1)),
                event(APP_A, PREPARING, APPLIED, Duration.ofDays(2)),
                event(APP_A, PREPARING, APPLIED, Duration.ofDays(3)),
                event(APP_B, null, SAVED, Duration.ZERO));

        List<FunnelVelocity.Transition> transitions = velocity();

        assertThat(transitions)
                .extracting(FunnelVelocity.Transition::fromStatus, FunnelVelocity.Transition::count)
                .containsExactly(tuple("PREPARING", 2), tuple("SAVED", 1));
    }

    private List<FunnelVelocity.Transition> velocity() {
        return service.getFunnelVelocity(USER).transitions();
    }

    private void givenLedger(ApplicationStatusEvent... ledger) {
        when(events.findByUserId(USER)).thenReturn(List.of(ledger));
    }

    private static ApplicationStatusEvent event(UUID applicationId, ApplicationStatus from,
                                                ApplicationStatus to, Duration sinceStart) {
        return new ApplicationStatusEvent(UUID.randomUUID(), applicationId, USER, from, to,
                START.plus(sinceStart), null);
    }
}
