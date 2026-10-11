package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import com.autoapplicant.port.in.auth.LinkedInIdentity;
import com.autoapplicant.port.in.auth.ResolveLinkedInUserUseCase;
import com.autoapplicant.port.out.security.IdentityProviderPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class LinkedInAuthService implements ResolveLinkedInUserUseCase {

  private static final String DEFAULT_CURRENCY = "DKK";

  private final UserRepositoryPort userRepo;
  private final ProfileRepositoryPort profileRepo;
  private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
  private final IdentityProviderPort identityProvider;

  public LinkedInAuthService(
      UserRepositoryPort userRepo,
      ProfileRepositoryPort profileRepo,
      ProfilePrivateInfoRepositoryPort privateInfoRepo,
      IdentityProviderPort identityProvider) {
    this.userRepo = userRepo;
    this.profileRepo = profileRepo;
    this.privateInfoRepo = privateInfoRepo;
    this.identityProvider = identityProvider;
  }

  @Override
  public User resolve(LinkedInIdentity identity) {
    Optional<User> byLinkedinId = userRepo.findByLinkedinId(identity.sub());
    if (byLinkedinId.isPresent()) {
      return returningMember(byLinkedinId.get(), identity);
    }
    if (!identity.emailVerified()) {
      throw new SecurityException("LinkedIn has not verified this email address");
    }
    return withVerifiedFirebaseAccount(findOrCreateByEmail(identity), identity);
  }

  /**
   * The LinkedIn id already identifies the member. Their Firebase account is only touched when
   * LinkedIn vouches for the very address the account is registered under.
   */
  private User returningMember(User user, LinkedInIdentity identity) {
    boolean sameVerifiedEmail =
        identity.emailVerified() && user.email().equalsIgnoreCase(identity.email());
    return sameVerifiedEmail ? withVerifiedFirebaseAccount(user, identity) : user;
  }

  private User findOrCreateByEmail(LinkedInIdentity identity) {
    Optional<User> byEmail = userRepo.findByEmail(identity.email());
    if (byEmail.isPresent()) {
      return linkLinkedInToExisting(byEmail.get(), identity.sub());
    }
    try {
      return createLinkedInUser(identity);
    } catch (DataIntegrityViolationException concurrentFirstSignIn) {
      // Two first sign-ins raced and the other one inserted the row: use it.
      return userRepo
          .findByLinkedinId(identity.sub())
          .or(() -> userRepo.findByEmail(identity.email()))
          .orElseThrow(() -> new IllegalStateException("Sign-in collided; please try again"));
    }
  }

  /**
   * The backend only accepts Firebase tokens with a verified email, so the Firebase account the
   * custom token is minted for must carry the LinkedIn-verified address.
   */
  private User withVerifiedFirebaseAccount(User user, LinkedInIdentity identity) {
    String preferredUid =
        user.firebaseUid() != null ? user.firebaseUid() : identity.fallbackFirebaseUid();
    String uid =
        identityProvider.ensureVerifiedAccount(
            preferredUid, identity.email(), identity.fullName());
    if (uid.equals(user.firebaseUid()) && user.emailVerified()) {
      return user;
    }
    return userRepo.save(withFirebaseAccount(user, uid));
  }

  /** Attach the LinkedIn identity to an account already registered under this email. */
  private User linkLinkedInToExisting(User existing, String linkedinSub) {
    return userRepo.save(
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
            existing.updatedAt()));
  }

  private static User withFirebaseAccount(User user, String firebaseUid) {
    return new User(
        user.id(),
        user.email(),
        user.googleId(),
        user.linkedinId(),
        firebaseUid,
        user.role(),
        true,
        user.onboardingComplete(),
        user.createdAt(),
        user.updatedAt());
  }

  /**
   * First LinkedIn sign-in: create the user with their linkedinId, a blank default profile, and,
   * when provided, their name. The Firebase uid is attached once the Firebase account exists.
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
