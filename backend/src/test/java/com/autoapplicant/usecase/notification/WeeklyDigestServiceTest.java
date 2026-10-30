package com.autoapplicant.usecase.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.notification.NotificationPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WeeklyDigestServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final PreferencesRepositoryPort preferencesRepo = Mockito.mock(PreferencesRepositoryPort.class);
    private final UserRepositoryPort userRepo = Mockito.mock(UserRepositoryPort.class);
    private final JobRepositoryPort jobRepo = Mockito.mock(JobRepositoryPort.class);
    private final NotificationPort notification = Mockito.mock(NotificationPort.class);

    private final WeeklyDigestService service =
            new WeeklyDigestService(preferencesRepo, userRepo, jobRepo, notification);

    private void weeklyUserExcluding(String negativeSignal) {
        UserPreferences prefs = new UserPreferences(UUID.randomUUID(), USER, null, null, null,
                List.of(negativeSignal), null, null, null, null, null, null, null, null,
                true, "WEEKLY", null, null, null);
        when(preferencesRepo.findAllWithNotificationsEnabled()).thenReturn(List.of(prefs));
        when(userRepo.findById(USER)).thenReturn(Optional.of(new User(USER, "a@example.com",
                null, null, null, UserRole.USER, true, true, null, null)));
    }

    private void onlyJobThisWeek(String title) {
        when(jobRepo.findActive(anyInt(), anyInt())).thenReturn(List.of(
                Job.builder().id(UUID.randomUUID()).title(title).postedAt(Instant.now()).build()));
    }

    @Test
    void excludingJavaDoesNotExcludeJavaScriptRoles() {
        weeklyUserExcluding("Java");
        onlyJobThisWeek("Frontend developer (JavaScript)");

        assertThat(service.sendDigests()).isEqualTo(1);
    }

    @Test
    void anExcludedSignalStillRemovesTheRole() {
        weeklyUserExcluding("Java");
        onlyJobThisWeek("Senior Java developer");

        assertThat(service.sendDigests()).isZero();
    }
}
