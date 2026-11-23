package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

class LocalUserProvisioningServiceTest {

    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final LocalUserProvisioningService service = new LocalUserProvisioningService(
            users, mock(ProfileRepositoryPort.class), mock(PlatformTransactionManager.class));

    @Test
    void a_request_that_loses_the_race_to_create_the_user_gets_the_winners_row() {
        User winner = new User(UUID.randomUUID(), LocalUserProvisioningService.LOCAL_EMAIL, null,
                null, null, UserRole.USER, true, false, Instant.now(), Instant.now());
        when(users.findByEmail(LocalUserProvisioningService.LOCAL_EMAIL))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(users.save(any())).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThat(service.provisionLocalUser()).isEqualTo(winner);
    }
}
