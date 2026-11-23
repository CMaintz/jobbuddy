package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.job.DanishPlaces;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
import java.util.List;
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
        events.publishEvent(new ProfileSavedEvent(userId));
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
        requirePlaceableArea(preferences.enrichmentArea());
        // Onboarding saves with a short payload of its own; a field it leaves out must keep
        // what Settings saved, not be wiped.
        UserPreferences saved = prefsRepo.findByUserId(userId).orElse(null);
        List<String> excluded = preferences.excludedCategories() != null || saved == null
                ? preferences.excludedCategoriesOrEmpty() : saved.excludedCategoriesOrEmpty();
        EnrichmentArea area = preferences.enrichmentArea() != null || saved == null
                ? preferences.enrichmentArea() : saved.enrichmentArea();
        return prefsRepo.save(new UserPreferences(null, userId,
                preferences.preferredLocations(), preferences.preferredMunicipalities(),
                preferences.positiveSignals(), preferences.negativeSignals(), preferences.excludedCompanies(),
                preferences.preferredRemoteTypes(), preferences.preferredEmploymentTypes(),
                preferences.preferredSeniority(), preferences.preferredIndustries(),
                preferences.salaryMin(), preferences.salaryMax(),
                preferences.maxCommuteKm(),
                preferences.notificationEnabled(), preferences.notificationFrequency(),
                preferences.weeklyApplicationGoal(), null, null,
                excluded, area));
    }

    /** An area the queue could not place would silently fall back to the default. */
    private static void requirePlaceableArea(EnrichmentArea area) {
        if (area == null) {
            return;
        }
        if (DanishPlaces.shared().locate(area.home()).isEmpty()) {
            throw new IllegalArgumentException("Vi kan ikke finde \"" + area.home()
                    + "\". Skriv et postnummer eller et bynavn, fx 8260 Viby J.");
        }
        if (!area.radiusInRange()) {
            throw new IllegalArgumentException("Afstanden skal være mellem "
                    + EnrichmentArea.MIN_RADIUS_KM + " og " + EnrichmentArea.MAX_RADIUS_KM + " km.");
        }
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
