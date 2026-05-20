package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LinkedInAuthService implements ResolveLinkedInUserUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;

    public LinkedInAuthService(UserRepositoryPort userRepo, ProfileRepositoryPort profileRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
    }

    @Override
    public User resolve(String linkedinSub, String email, String fullName) {
        // Try existing LinkedIn user
        Optional<User> byLinkedinId = userRepo.findByLinkedinId(linkedinSub);
        if (byLinkedinId.isPresent()) {
            return byLinkedinId.get();
        }

        // Link to existing email account
        Optional<User> byEmail = userRepo.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            User updated = new User(existing.id(), existing.email(),
                    existing.googleId(), linkedinSub, existing.firebaseUid(), existing.role(),
                    existing.emailVerified(), existing.createdAt(), existing.updatedAt());
            return userRepo.save(updated);
        }

        // New user — create with linkedinId; firebaseUid gets set when they sign in via custom token
        User newUser = new User(null, email, null, linkedinSub, null, UserRole.USER, true, null, null);
        User savedUser = userRepo.save(newUser);

        Profile profile = new Profile(null, savedUser.id(), fullName, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        profileRepo.save(profile);

        return savedUser;
    }
}
