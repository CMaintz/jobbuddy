package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LinkedInAuthService implements ResolveLinkedInUserUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;

    public LinkedInAuthService(UserRepositoryPort userRepo, ProfileRepositoryPort profileRepo,
                               ProfilePrivateInfoRepositoryPort privateInfoRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.privateInfoRepo = privateInfoRepo;
    }

    @Override
    public User resolve(String linkedinSub, String email, String fullName) {
        Optional<User> byLinkedinId = userRepo.findByLinkedinId(linkedinSub);
        if (byLinkedinId.isPresent()) {
            return byLinkedinId.get();
        }
        Optional<User> byEmail = userRepo.findByEmail(email);
        if (byEmail.isPresent()) {
            return linkLinkedInToExisting(byEmail.get(), linkedinSub);
        }
        return createLinkedInUser(linkedinSub, email, fullName);
    }

    /** Attach the LinkedIn identity to an account already registered under this email. */
    private User linkLinkedInToExisting(User existing, String linkedinSub) {
        User updated = new User(existing.id(), existing.email(),
                existing.googleId(), linkedinSub, existing.firebaseUid(), existing.role(),
                existing.emailVerified(), existing.onboardingComplete(), existing.createdAt(), existing.updatedAt());
        return userRepo.save(updated);
    }

    /**
     * First LinkedIn sign-in: create the user with their linkedinId (firebaseUid gets set when they
     * later sign in via custom token), a blank default profile, and — when provided — their name.
     */
    private User createLinkedInUser(String linkedinSub, String email, String fullName) {
        User newUser = new User(null, email, null, linkedinSub, null, UserRole.USER, true, false, null, null);
        User savedUser = userRepo.save(newUser);

        Profile profile = new Profile(null, savedUser.id(), null, null, null,
                List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        profileRepo.save(profile);

        if (fullName != null && !fullName.isBlank()) {
            ProfilePrivateInfo privateInfo = new ProfilePrivateInfo(null, savedUser.id(),
                    fullName, null, null, null, null, null, null, null);
            privateInfoRepo.save(privateInfo);
        }

        return savedUser;
    }
}
