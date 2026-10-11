package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LinkedInAuthService implements ResolveLinkedInUserUseCase {

  private static final String DEFAULT_CURRENCY = "DKK";

  private final UserRepositoryPort userRepo;
  private final ProfileRepositoryPort profileRepo;
  private final ProfilePrivateInfoRepositoryPort privateInfoRepo;

  public LinkedInAuthService(
      UserRepositoryPort userRepo,
      ProfileRepositoryPort profileRepo,
      ProfilePrivateInfoRepositoryPort privateInfoRepo) {
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
    this.privateInfoRepo = privateInfoRepo;
  }

  @Override
  public User resolve(LinkedInIdentity identity) {
    Optional<User> byLinkedinId = userRepo.findByLinkedinId(identity.sub());
    if (byLinkedinId.isPresent()) {
      return byLinkedinId.get();
    }
    if (!identity.emailVerified()) {
      throw new SecurityException("LinkedIn has not verified this email address");
    }
    Optional<User> byEmail = userRepo.findByEmail(identity.email());
    if (byEmail.isPresent()) {
      return linkLinkedInToExisting(byEmail.get(), identity.sub());
    }
    return createLinkedInUser(identity);
  }

  /** Attach the LinkedIn identity to an account already registered under this email. */
  private User linkLinkedInToExisting(User existing, String linkedinSub) {
    User updated =
        new User(
            existing.id(),
            existing.email(),
            existing.googleId(),
            linkedinSub,
            existing.firebaseUid(),
            existing.role(),
            existing.emailVerified(),
            existing.onboardingComplete(),
            existing.createdAt(),
            existing.updatedAt());
    return userRepo.save(updated);
  }

  /**
   * First LinkedIn sign-in: create the user with their linkedinId (firebaseUid gets set when they
   * later sign in via custom token), a blank default profile, and, when provided, their name.
   */
  private User createLinkedInUser(LinkedInIdentity identity) {
    User newUser =
        new User(
            null,
            identity.email(),
            null,
            identity.sub(),
            null,
            UserRole.USER,
            identity.emailVerified(),
            false,
            null,
            null);
    User savedUser = userRepo.save(newUser);

    Profile profile =
        new Profile(
            null,
            savedUser.id(),
            null,
            null,
            null,
            List.of(),
            List.of(),
            null,
            null,
            DEFAULT_CURRENCY,
            null,
            null,
            null,
            null);
    profileRepo.save(profile);

    String fullName = identity.fullName();
    if (fullName != null && !fullName.isBlank()) {
      ProfilePrivateInfo privateInfo =
          new ProfilePrivateInfo(
              null, savedUser.id(), fullName, null, null, null, null, null, null, null);
      privateInfoRepo.save(privateInfo);
    }

    return savedUser;
  }
}
