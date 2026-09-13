package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedInAuthServiceTest {

    @Mock UserRepositoryPort               userRepo;
    @Mock ProfileRepositoryPort            profileRepo;
    @Mock ProfilePrivateInfoRepositoryPort privateInfoRepo;

    LinkedInAuthService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LinkedInAuthService(userRepo, profileRepo, privateInfoRepo);
    }

    @Test
    void returns_existing_linkedin_user_without_touching_anything_else() {
        User existing = user(userId, "sub1@example.com", "sub1");
        when(userRepo.findByLinkedinId("sub1")).thenReturn(Optional.of(existing));

        User result = service.resolve("sub1", "sub1@example.com", "Sub One");

        assertThat(result).isSameAs(existing);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(profileRepo, privateInfoRepo);
    }

    @Test
    void links_linkedin_sub_to_existing_email_account_preserving_other_fields() {
        when(userRepo.findByLinkedinId("sub2")).thenReturn(Optional.empty());
        User byEmail = user(userId, "linked@example.com", null);
        when(userRepo.findByEmail("linked@example.com")).thenReturn(Optional.of(byEmail));
        User saved = user(userId, "linked@example.com", "sub2");
        when(userRepo.save(any())).thenReturn(saved);

        User result = service.resolve("sub2", "linked@example.com", "Linked");

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().linkedinId()).isEqualTo("sub2");
        assertThat(cap.getValue().id()).isEqualTo(userId);
        assertThat(cap.getValue().email()).isEqualTo("linked@example.com");
        verifyNoInteractions(profileRepo, privateInfoRepo);
    }

    @Test
    void creates_new_user_with_profile_and_private_info_when_full_name_given() {
        when(userRepo.findByLinkedinId("sub3")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        User saved = user(userId, "new@example.com", "sub3");
        when(userRepo.save(any())).thenReturn(saved);

        User result = service.resolve("sub3", "new@example.com", "New User");

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCap.capture());
        assertThat(userCap.getValue().linkedinId()).isEqualTo("sub3");
        assertThat(userCap.getValue().email()).isEqualTo("new@example.com");
        assertThat(userCap.getValue().role()).isEqualTo(UserRole.USER);

        verify(profileRepo).save(any());
        ArgumentCaptor<ProfilePrivateInfo> infoCap = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
        verify(privateInfoRepo).save(infoCap.capture());
        assertThat(infoCap.getValue().fullName()).isEqualTo("New User");
        assertThat(infoCap.getValue().userId()).isEqualTo(userId);
    }

    @Test
    void creates_new_user_without_private_info_when_full_name_blank() {
        when(userRepo.findByLinkedinId("sub4")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("blank@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenReturn(user(userId, "blank@example.com", "sub4"));

        service.resolve("sub4", "blank@example.com", "   ");

        verify(profileRepo).save(any());
        verify(privateInfoRepo, never()).save(any());
    }

    private User user(UUID id, String email, String linkedinId) {
        return new User(id, email, null, linkedinId, null,
                UserRole.USER, true, false, Instant.now(), Instant.now());
    }
}
