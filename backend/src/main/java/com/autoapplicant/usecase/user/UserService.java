package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements GetUserProfileUseCase, UpdateUserProfileUseCase, UpdatePreferencesUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;

    public UserService(UserRepositoryPort userRepo,
                       ProfileRepositoryPort profileRepo,
                       PreferencesRepositoryPort prefsRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
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
        return profileRepo.save(new Profile(null, userId, profile.headline(),
                profile.summary(), profile.yearsExperience(), profile.skills(), profile.technologies(),
                profile.languages(), profile.desiredSalaryMin(), profile.desiredSalaryMax(),
                profile.desiredCurrency(), profile.remotePreference(), profile.employmentTypePreference(),
                null, null));
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
                preferences.notificationEnabled(), preferences.notificationFrequency(), null, null));
    }
}
