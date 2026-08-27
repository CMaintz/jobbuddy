package com.autoapplicant.usecase.notification;

import com.autoapplicant.domain.notification.Nudge;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.notification.GetNudgesUseCase;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class DueReminderDigestServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final PreferencesRepositoryPort preferencesRepo = Mockito.mock(PreferencesRepositoryPort.class);
    private final UserRepositoryPort userRepo = Mockito.mock(UserRepositoryPort.class);
    private final GetNudgesUseCase nudges = Mockito.mock(GetNudgesUseCase.class);
    private final NotificationPort notification = Mockito.mock(NotificationPort.class);

    private final DueReminderDigestService service =
            new DueReminderDigestService(preferencesRepo, userRepo, nudges, notification);

    private static UserPreferences prefs(UUID userId) {
        return new UserPreferences(UUID.randomUUID(), userId, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null,
                true, "DAILY", null, null, null);
    }

    @BeforeEach
    void setUp() {
        when(preferencesRepo.findAllWithNotificationsEnabled()).thenReturn(List.of(prefs(USER)));
        when(userRepo.findById(USER)).thenReturn(Optional.of(
                new User(USER, "user@example.dk", null, null, null, null, false, false, null, null)));
    }

    private static Nudge outreachDue(String company) {
        return new Nudge(Nudge.NudgeType.OUTREACH_FOLLOW_UP, null, null, UUID.randomUUID(), null,
                null, company, LocalDate.of(2026, 8, 26), null, "Mette Hansen");
    }

    private static Nudge deadlineSoon() {
        return Nudge.forApplication(Nudge.NudgeType.DEADLINE_SOON, UUID.randomUUID(),
                UUID.randomUUID(), "Backend developer", "Acme", LocalDate.of(2026, 9, 1), null);
    }

    @Test
    void aDueFollowUpIsMailed() {
        when(nudges.getNudges(USER)).thenReturn(List.of(outreachDue("Acme A/S")));

        assertThat(service.sendDueReminders()).isEqualTo(1);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notification).send(Mockito.eq("user@example.dk"), anyString(), body.capture());
        assertThat(body.getValue()).contains("Acme A/S").contains("Mette Hansen");
    }

    @Test
    void nothingDueIsNotNews() {
        when(nudges.getNudges(USER)).thenReturn(List.of());
        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void onlyTheDatedTypesAreMailed() {
        // Deadlines and the 14-day heuristic already surface in the app and do not change hour to
        // hour; mailing them daily is how a digest gets filtered.
        when(nudges.getNudges(USER)).thenReturn(List.of(deadlineSoon()));
        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void theSubjectCountsWhatIsDue() {
        when(nudges.getNudges(USER)).thenReturn(List.of(outreachDue("A"), outreachDue("B")));
        service.sendDueReminders();
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notification).send(anyString(), subject.capture(), anyString());
        assertThat(subject.getValue()).isEqualTo("2 follow-ups due today");
    }

    @Test
    void aUserWithoutAnEmailIsSkippedRatherThanFailing() {
        when(userRepo.findById(USER)).thenReturn(Optional.of(
                new User(USER, null, null, null, null, null, false, false, null, null)));
        when(nudges.getNudges(USER)).thenReturn(List.of(outreachDue("Acme")));

        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void nobodyOptedInMeansNoWork() {
        when(preferencesRepo.findAllWithNotificationsEnabled()).thenReturn(List.of());
        assertThat(service.sendDueReminders()).isZero();
        Mockito.verifyNoInteractions(nudges);
    }
}
