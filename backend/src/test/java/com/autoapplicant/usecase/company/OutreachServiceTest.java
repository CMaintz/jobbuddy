package com.autoapplicant.usecase.company;

import com.autoapplicant.domain.company.OutreachContact;
import com.autoapplicant.domain.company.OutreachStatus;
import com.autoapplicant.port.out.company.OutreachContactRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutreachServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    /** In-memory port so the service's own rules are what is under test. */
    private static class InMemoryRepo implements OutreachContactRepositoryPort {
        final List<OutreachContact> rows = new ArrayList<>();

        @Override public OutreachContact save(OutreachContact contact) {
            OutreachContact stored = contact.id() != null ? contact
                    : new OutreachContact(UUID.randomUUID(), contact.userId(), contact.companyId(),
                            contact.companyName(), contact.status(), contact.channel(),
                            contact.contactName(), contact.contactedAt(), contact.followUpDue(),
                            contact.notes(), contact.createdAt(), contact.updatedAt());
            rows.removeIf(r -> r.id().equals(stored.id()));
            rows.add(stored);
            return stored;
        }
        @Override public Optional<OutreachContact> findById(UUID id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }
        @Override public Optional<OutreachContact> findByUserAndCompany(UUID userId, UUID companyId) {
            return rows.stream()
                    .filter(r -> r.userId().equals(userId) && companyId.equals(r.companyId()))
                    .findFirst();
        }
        @Override public List<OutreachContact> findByUserId(UUID userId) {
            return rows.stream().filter(r -> r.userId().equals(userId)).toList();
        }
        @Override public void delete(UUID id) { rows.removeIf(r -> r.id().equals(id)); }
    }

    private final InMemoryRepo repo = new InMemoryRepo();
    private final OutreachService service =
            new OutreachService(repo, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void trackingTheSameCompanyTwiceKeepsOneThreadOfContact() {
        UUID companyId = UUID.randomUUID();
        OutreachContact first = service.track(USER, companyId, "Acme A/S", "Mette Hansen");
        OutreachContact second = service.track(USER, companyId, "Acme A/S", null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.contactName()).isEqualTo("Mette Hansen");
        assertThat(repo.rows).hasSize(1);
    }

    @Test
    void aTrackedOutreachStartsOnTheListAndUncontacted() {
        OutreachContact saved = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        assertThat(saved.status()).isEqualTo(OutreachStatus.SAVED);
        assertThat(saved.contactedAt()).isNull();
        assertThat(saved.followUpDue()).isNull();
    }

    @Test
    void aCompanyNameIsRequiredBecauseTheRecordOutlivesTheCompanyRow() {
        assertThatThrownBy(() -> service.track(USER, UUID.randomUUID(), "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markingContactedStampsTheTimeAndSchedulesTheFollowUp() {
        OutreachContact saved = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        OutreachContact contacted = service.update(USER, saved.id(), OutreachStatus.CONTACTED,
                "EMAIL", null, null);

        assertThat(contacted.contactedAt()).isEqualTo(NOW);
        assertThat(contacted.followUpDue()).isEqualTo(
                LocalDate.now(Clock.fixed(NOW, ZoneOffset.UTC))
                        .plusDays(OutreachService.DEFAULT_FOLLOW_UP_DAYS));
    }

    @Test
    void anExplicitFollowUpDateWinsOverTheDefault() {
        OutreachContact saved = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        LocalDate chosen = LocalDate.of(2026, 9, 30);
        assertThat(service.update(USER, saved.id(), OutreachStatus.CONTACTED, null, chosen, null)
                .followUpDue()).isEqualTo(chosen);
    }

    @Test
    void alaterEditDoesNotResetTheContactClock() {
        OutreachContact saved = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        service.update(USER, saved.id(), OutreachStatus.CONTACTED, "EMAIL", null, null);
        OutreachContact edited = service.update(USER, saved.id(), OutreachStatus.CONTACTED,
                null, null, "sent a second note");

        assertThat(edited.contactedAt()).isEqualTo(NOW);
        assertThat(edited.notes()).isEqualTo("sent a second note");
        assertThat(edited.channel()).as("an unset field stays as it was").isEqualTo("EMAIL");
    }

    @Test
    void followUpsThatAreDueSortToTheTop() {
        OutreachContact overdue = service.track(USER, UUID.randomUUID(), "Overdue A/S", null);
        service.update(USER, overdue.id(), OutreachStatus.CONTACTED, null,
                LocalDate.of(2026, 8, 1), null);
        service.track(USER, UUID.randomUUID(), "Just saved ApS", null);

        assertThat(service.list(USER)).first()
                .extracting(OutreachContact::companyName).isEqualTo("Overdue A/S");
    }

    @Test
    void aClosedThreadIsNeverDueForFollowUp() {
        OutreachContact saved = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        OutreachContact closed = service.update(USER, saved.id(), OutreachStatus.CLOSED, null,
                LocalDate.of(2026, 8, 1), null);
        assertThat(closed.isFollowUpDue(LocalDate.of(2026, 8, 26))).isFalse();
    }

    @Test
    void anotherUsersOutreachIsNotReachable() {
        OutreachContact mine = service.track(USER, UUID.randomUUID(), "Acme A/S", null);
        assertThatThrownBy(() -> service.update(OTHER_USER, mine.id(), OutreachStatus.CLOSED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.untrack(OTHER_USER, mine.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.rows).hasSize(1);
    }
}
