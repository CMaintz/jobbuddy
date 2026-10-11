package com.autoapplicant.usecase.auth;

import com.autoapplicant.domain.common.AccountExistsException;
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

  /**
   * Resolves only through an existing LinkedIn link. LinkedIn is not authoritative for an email (a
   * former employer's mailbox, a recycled address), so an account that already holds the address
   * is never linked or signed into from here; that takes a signed-in linking flow.
   */
  @Override
  public User resolve(LinkedInIdentity identity) {
    Optional<User> byLinkedinId = userRepo.findByLinkedinId(identity.sub());
    if (byLinkedinId.isPresent()) {
      return returningMember(byLinkedinId.get(), identity);
    }
    if (!identity.emailVerified()) {
      throw new SecurityException("LinkedIn has not verified this email address");
    }
    if (userRepo.findByEmail(identity.email()).isPresent()
        || identityProvider.emailInUse(identity.email())) {
      throw new AccountExistsException();
    }
    return createNewMember(identity);
  }

  /**
   * The LinkedIn id already identifies the member. Their Firebase account is only given the email
   * when LinkedIn vouches for the very address the account is registered under, since the backend
   * accepts only tokens with a verified email.
   */
  private User returningMember(User user, LinkedInIdentity identity) {
    boolean sameVerifiedEmail =
        identity.emailVerified() && user.email().equalsIgnoreCase(identity.email());
    if (!sameVerifiedEmail) {
      return user;
    }
    String uid = user.firebaseUid() != null ? user.firebaseUid() : identity.fallbackFirebaseUid();
    identityProvider.provisionVerifiedAccount(uid, user.email(), identity.fullName());
    if (uid.equals(user.firebaseUid()) && user.emailVerified()) {
      return user;
    }
    return userRepo.save(withFirebaseAccount(user, uid));
  }

  /**
   * The Firebase account comes first: if another account claimed the email meanwhile, Firebase
   * refuses it before any app row exists.
   */
  private User createNewMember(LinkedInIdentity identity) {
    String uid = identity.fallbackFirebaseUid();
    identityProvider.provisionVerifiedAccount(uid, identity.email(), identity.fullName());
    try {
      return createLinkedInUser(identity, uid);
    } catch (DataIntegrityViolationException concurrentInsert) {
      // The same member signing in twice at once: use the row the other request wrote. Anything
      // else holding the email is another account.
      return userRepo.findByLinkedinId(identity.sub()).orElseThrow(AccountExistsException::new);
    }
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
   * First LinkedIn sign-in: create the user with their linkedinId and Firebase uid, a blank
   * default profile, and, when provided, their name.
   */
  private User createLinkedInUser(LinkedInIdentity identity, String firebaseUid) {
    User newUser =
        new User(
            null,
            identity.email(),
            null,
            identity.sub(),
            firebaseUid,
            UserRole.USER,
            true,
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
