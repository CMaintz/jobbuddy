package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class UserService implements GetUserProfileUseCase, UpdateUserProfileUseCase, UpdatePreferencesUseCase, CompleteOnboardingUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final ApplicationEventPublisher events;

    public UserService(UserRepositoryPort userRepo,
                       ProfileRepositoryPort profileRepo,
                       PreferencesRepositoryPort prefsRepo,
                       ApplicationEventPublisher events) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.events = events;
    }

    @Override
    public Optional<User> getUser(UUID userId) {
        return userRepo.findById(userId);
    }

    @Override
    public Optional<Profile> getProfile(UUID userId) {
        return profileRepo.findByUserId(userId);
    }

    @Override
    public Profile updateProfile(UUID userId, Profile profile) {
        // Merge semantics: null means "keep existing", so clients can send partial updates.
        // Text fields are cleared with an empty string, lists with an empty list.
        Profile existing = profileRepo.findByUserId(userId).orElse(null);
        Profile saved = profileRepo.save(new Profile(null, userId,
                merged(profile.headline(), existing, Profile::headline),
                merged(profile.summary(), existing, Profile::summary),
                merged(profile.yearsExperience(), existing, Profile::yearsExperience),
                merged(profile.languages(), existing, Profile::languages),
                merged(profile.interests(), existing, Profile::interests),
                merged(profile.desiredSalaryMin(), existing, Profile::desiredSalaryMin),
                merged(profile.desiredSalaryMax(), existing, Profile::desiredSalaryMax),
                merged(profile.desiredCurrency(), existing, Profile::desiredCurrency),
                merged(profile.remotePreference(), existing, Profile::remotePreference),
                merged(profile.employmentTypePreference(), existing, Profile::employmentTypePreference),
                null, null));
        // The embedding refresh is an AI call; ProfileEmbeddingUpdater does it off this thread.
        events.publishEvent(new ProfileSavedEvent(userId, saved));
        return saved;
    }

    private static <T> T merged(T incoming, Profile existing, Function<Profile, T> getter) {
        if (incoming != null) return incoming;
        return existing != null ? getter.apply(existing) : null;
    }

    @Override
    public Optional<UserPreferences> getPreferences(UUID userId) {
        return prefsRepo.findByUserId(userId);
    }

    @Override
    public UserPreferences updatePreferences(UUID userId, UserPreferences preferences) {
        return prefsRepo.save(new UserPreferences(null, userId,
                preferences.preferredLocations(), preferences.preferredMunicipalities(),
                preferences.positiveSignals(), preferences.negativeSignals(), preferences.excludedCompanies(),
                preferences.preferredRemoteTypes(), preferences.preferredEmploymentTypes(),
                preferences.preferredSeniority(), preferences.preferredIndustries(),
                preferences.salaryMin(), preferences.salaryMax(),
                preferences.maxCommuteKm(),
                preferences.notificationEnabled(), preferences.notificationFrequency(),
                preferences.weeklyApplicationGoal(), null, null));
    }

    @Override
    public void completeOnboarding(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        userRepo.save(new User(user.id(), user.email(), user.googleId(),
                user.linkedinId(), user.firebaseUid(), user.role(), user.emailVerified(),
                true, user.createdAt(), user.updatedAt()));
    }
}
