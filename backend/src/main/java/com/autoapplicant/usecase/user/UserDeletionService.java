package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.user.DeleteUserAccountUseCase;
import com.autoapplicant.port.out.security.IdentityProviderPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDeletionService implements DeleteUserAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDeletionService.class);

    private final UserRepositoryPort userRepo;
    private final IdentityProviderPort identityProvider;

    public UserDeletionService(UserRepositoryPort userRepo, IdentityProviderPort identityProvider) {
        this.userRepo = userRepo;
        this.identityProvider = identityProvider;
    }

    @Override
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Delete from DB: all user-owned tables cascade via ON DELETE CASCADE on users.id FK.
        userRepo.deleteById(userId);

        // Delete the sign-in provider's account too, best effort: log if it fails but don't roll
        // back the DB delete.
        if (user.firebaseUid() != null) {
            try {
                identityProvider.deleteAccount(user.firebaseUid());
            } catch (IllegalStateException e) {
                log.error("DB account deleted but the sign-in provider kept account {}",
                        user.firebaseUid(), e);
            }
        }
    }
}
