package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.user.DeleteUserAccountUseCase;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserDeletionService implements DeleteUserAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);

    private final UserRepositoryPort userRepo;
    private final FirebaseAuth firebaseAuth;

    public UserDeletionService(UserRepositoryPort userRepo, FirebaseAuth firebaseAuth) {
        this.userRepo = userRepo;
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Delete from DB — all user-owned tables cascade via ON DELETE CASCADE on users.id FK.
        userRepo.deleteById(userId);

        // Delete from Firebase — best effort; log if it fails but don't roll back the DB delete.
        if (user.firebaseUid() != null) {
            try {
                firebaseAuth.deleteUser(user.firebaseUid());
            } catch (FirebaseAuthException e) {
                log.error("DB account deleted but Firebase user deletion failed for UID {}: {}", user.firebaseUid(), e.getMessage());
            }
        }
    }
}
