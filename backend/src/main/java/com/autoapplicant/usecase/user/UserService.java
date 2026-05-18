package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
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
public class UserService implements GetUserProfileUseCase, UpdateUserProfileUseCase, UpdatePreferencesUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final FirebaseAuth firebaseAuth;

    public UserService(UserRepositoryPort userRepo, ProfileRepositoryPort profileRepo,
                       PreferencesRepositoryPort prefsRepo, FirebaseAuth firebaseAuth) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Called by FirebaseTokenFilter on every authenticated request.
     * Looks up the user by firebaseUid, creating a new record on first login.
     * Also sets the Firebase Custom Claim for role so subsequent tokens carry it.
     */
    public User findOrCreateUserFromFirebase(String firebaseUid, String email, String name) {
        Optional<User> existing = userRepo.findByFirebaseUid(firebaseUid);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Link to an existing account that shares the same email
        Optional<User> byEmail = userRepo.findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            User linked = new User(user.id(), user.email(), null, user.googleId(),
                    user.linkedinId(), firebaseUid, user.role(), true,
                    user.createdAt(), user.updatedAt());
            User saved = userRepo.save(linked);
            setRoleCustomClaim(firebaseUid, saved.role());
            return saved;
        }

        // Brand new user — create account and default profile
        User newUser = new User(null, email, null, null, null, firebaseUid,
                UserRole.USER, true, null, null);
        User saved = userRepo.save(newUser);

        String fullName = (name != null && !name.isBlank()) ? name : email;
        Profile profile = new Profile(null, saved.id(), fullName, null, null,
                null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(),
                null, null, "DKK", null, null, null, null);
        profileRepo.save(profile);

        setRoleCustomClaim(firebaseUid, saved.role());
        return saved;
    }

    /**
     * Promotes (or demotes) a user's role in the DB and updates the Firebase Custom Claim
     * so their next token refresh reflects the change.
     */
    public User setUserRole(UUID userId, UserRole newRole) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        User updated = new User(user.id(), user.email(), user.passwordHash(), user.googleId(),
                user.linkedinId(), user.firebaseUid(), newRole, user.emailVerified(),
                user.createdAt(), user.updatedAt());
        User saved = userRepo.save(updated);
        if (saved.firebaseUid() != null) {
            setRoleCustomClaim(saved.firebaseUid(), newRole);
        }
        return saved;
    }

    private void setRoleCustomClaim(String firebaseUid, UserRole role) {
        try {
            firebaseAuth.setCustomUserClaims(firebaseUid, Map.of("role", role.name()));
        } catch (FirebaseAuthException e) {
            log.warn("Failed to set custom claim for Firebase UID {}: {}", firebaseUid, e.getMessage());
        }
    }

    @Override
    public Optional<Profile> getProfile(UUID userId) {
        return profileRepo.findByUserId(userId);
    }

    @Override
    public Profile updateProfile(UUID userId, Profile profile) {
        return profileRepo.save(new Profile(null, userId, profile.fullName(), profile.headline(),
                profile.summary(), profile.location(), profile.municipality(),
                profile.linkedinUrl(), profile.githubUrl(), profile.websiteUrl(), profile.phone(),
                profile.photoUrl(), profile.yearsExperience(), profile.skills(), profile.technologies(),
                profile.languages(), profile.desiredSalaryMin(), profile.desiredSalaryMax(),
                profile.desiredCurrency(), profile.remotePreference(), profile.employmentTypePreference(),
                null, null));
    }

    @Override
    public UserPreferences updatePreferences(UUID userId, UserPreferences preferences) {
        return prefsRepo.save(new UserPreferences(null, userId,
                preferences.preferredLocations(), preferences.preferredMunicipalities(),
                preferences.positiveSignals(), preferences.negativeSignals(), preferences.excludedCompanies(),
                preferences.preferredRemoteTypes(), preferences.preferredEmploymentTypes(),
                preferences.preferredSeniority(), preferences.salaryMin(), preferences.salaryMax(),
                preferences.maxCommuteKm(),
                preferences.notificationEnabled(), preferences.notificationFrequency(), null, null));
    }
}
