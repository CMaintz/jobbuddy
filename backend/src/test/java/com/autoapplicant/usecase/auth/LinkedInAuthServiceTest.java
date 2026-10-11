package com.autoapplicant.usecase.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
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

        User result = service.resolve(verified("sub1", "sub1@example.com", "Sub One"));

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

        User result = service.resolve(verified("sub2", "linked@example.com", "Linked"));

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

        User result = service.resolve(verified("sub3", "new@example.com", "New User"));

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

        service.resolve(verified("sub4", "blank@example.com", "   "));

        verify(profileRepo).save(any());
        verify(privateInfoRepo, never()).save(any());
    }

    @Test
    void returns_existing_linkedin_user_even_when_email_is_unverified() {
        User existing = user(userId, "sub5@example.com", "sub5");
        when(userRepo.findByLinkedinId("sub5")).thenReturn(Optional.of(existing));

        User result = service.resolve(new LinkedInIdentity("sub5", "sub5@example.com", false, "Five"));

        assertThat(result).isSameAs(existing);
        verify(userRepo, never()).save(any());
    }

    @Test
    void refuses_to_link_an_existing_email_account_on_an_unverified_email() {
        when(userRepo.findByLinkedinId("sub6")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(
                new LinkedInIdentity("sub6", "victim@example.com", false, "Attacker")))
                .isInstanceOf(SecurityException.class);

        verify(userRepo, never()).findByEmail(any());
        verify(userRepo, never()).save(any());
        verifyNoInteractions(profileRepo, privateInfoRepo);
    }

    @Test
    void refuses_to_create_an_account_on_an_unverified_email() {
        when(userRepo.findByLinkedinId("sub7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(
                new LinkedInIdentity("sub7", "fresh@example.com", false, "Fresh")))
                .isInstanceOf(SecurityException.class);

        verify(userRepo, never()).save(any());
        verifyNoInteractions(profileRepo, privateInfoRepo);
    }

    private LinkedInIdentity verified(String sub, String email, String fullName) {
        return new LinkedInIdentity(sub, email, true, fullName);
    }

    private User user(UUID id, String email, String linkedinId) {
        return new User(id, email, null, linkedinId, null,
                UserRole.USER, true, false, Instant.now(), Instant.now());
    }
}
