package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.auth.ProvisionFirebaseUserUseCase;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class FirebaseUserProvisioningService implements ProvisionFirebaseUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(FirebaseUserProvisioningService.class);

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final FirebaseAuth firebaseAuth;

    public FirebaseUserProvisioningService(UserRepositoryPort userRepo,
                                           ProfileRepositoryPort profileRepo,
                                           ProfilePrivateInfoRepositoryPort privateInfoRepo,
                                           FirebaseAuth firebaseAuth) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.privateInfoRepo = privateInfoRepo;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public User findOrCreate(String firebaseUid, String email, String name) {
        Optional<User> existing = userRepo.findByFirebaseUid(firebaseUid);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Link to an existing account that shares the same email
        Optional<User> byEmail = userRepo.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            User linked = new User(user.id(), user.email(), user.googleId(),
                    user.linkedinId(), firebaseUid, user.role(), true,
                    user.createdAt(), user.updatedAt());
            User saved = userRepo.save(linked);
            syncRoleClaim(firebaseUid, saved.role());
            return saved;
        }

        // Brand new user — create account and default profile
        User newUser = new User(null, email, null, null, firebaseUid,
                UserRole.USER, true, null, null);
        User saved = userRepo.save(newUser);

        profileRepo.save(new Profile(null, saved.id(), null, null, null,
                List.of(), List.of(), List.of(),
                null, null, "DKK", null, null, null, null));

        if (name != null && !name.isBlank()) {
            privateInfoRepo.save(new ProfilePrivateInfo(null, saved.id(),
                    name, null, null, null, null, null, null, null));
        }

        syncRoleClaim(firebaseUid, saved.role());
        return saved;
    }

    @Override
    public User setRole(UUID userId, UserRole newRole) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        User updated = new User(user.id(), user.email(), user.googleId(),
                user.linkedinId(), user.firebaseUid(), newRole, user.emailVerified(),
                user.createdAt(), user.updatedAt());
        User saved = userRepo.save(updated);
        if (saved.firebaseUid() != null) {
            syncRoleClaim(saved.firebaseUid(), newRole);
        }
        return saved;
    }

    private void syncRoleClaim(String firebaseUid, UserRole role) {
        try {
            firebaseAuth.setCustomUserClaims(firebaseUid, Map.of("role", role.name()));
        } catch (FirebaseAuthException e) {
            log.warn("Failed to set custom claim for Firebase UID {}: {}", firebaseUid, e.getMessage());
        }
    }
}
