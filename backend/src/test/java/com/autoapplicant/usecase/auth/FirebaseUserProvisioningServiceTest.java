package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.user.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseUserProvisioningServiceTest {

    @Mock UserRepositoryPort               userRepo;
    @Mock ProfileRepositoryPort            profileRepo;
    @Mock ProfilePrivateInfoRepositoryPort privateInfoRepo;
    @Mock FirebaseAuth                     firebaseAuth;

    FirebaseUserProvisioningService service;
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FirebaseUserProvisioningService(userRepo, profileRepo, privateInfoRepo, firebaseAuth);
    }

    @Test
    void creates_new_user_when_firebase_uid_and_email_are_unknown() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@example.com")).thenReturn(Optional.empty());
        User saved = user(userId, "new@example.com", "uid1");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        User result = service.findOrCreate("uid1", "new@example.com", "New User");

        assertThat(result).isSameAs(saved);
        verify(userRepo).save(any());
        verify(profileRepo).save(any());
        verify(firebaseAuth).setCustomUserClaims(eq("uid1"), any());
    }

    @Test
    void returns_existing_user_when_firebase_uid_already_known() throws FirebaseAuthException {
        User existing = user(userId, "alice@example.com", "uid1");
        when(userRepo.findByFirebaseUid("uid1")).thenReturn(Optional.of(existing));

        User result = service.findOrCreate("uid1", "alice@example.com", "Alice");

        assertThat(result).isSameAs(existing);
        verify(userRepo, never()).save(any());
        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void links_firebase_uid_to_existing_email_account() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid("uid2")).thenReturn(Optional.empty());
        User byEmail = user(userId, "linked@example.com", null);
        when(userRepo.findByEmail("linked@example.com")).thenReturn(Optional.of(byEmail));
        User linked = user(userId, "linked@example.com", "uid2");
        when(userRepo.save(any())).thenReturn(linked);

        service.findOrCreate("uid2", "linked@example.com", "Linked");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(cap.capture());
        assertThat(cap.getValue().firebaseUid()).isEqualTo("uid2");
    }

    @Test
    void new_user_gets_default_profile_and_private_info_created() throws FirebaseAuthException {
        when(userRepo.findByFirebaseUid(any())).thenReturn(Optional.empty());
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        User saved = user(userId, "p@example.com", "uid3");
        when(userRepo.save(any())).thenReturn(saved);
        when(profileRepo.save(any())).thenReturn(null);

        service.findOrCreate("uid3", "p@example.com", "Profile User");

        verify(profileRepo).save(any());
        ArgumentCaptor<ProfilePrivateInfo> cap = ArgumentCaptor.forClass(ProfilePrivateInfo.class);
        verify(privateInfoRepo).save(cap.capture());
        assertThat(cap.getValue().fullName()).isEqualTo("Profile User");
        assertThat(cap.getValue().userId()).isEqualTo(userId);
    }

    private User user(UUID id, String email, String firebaseUid) {
        return new User(id, email, null, null, firebaseUid,
                UserRole.USER, true, false, Instant.now(), Instant.now());
    }
}
