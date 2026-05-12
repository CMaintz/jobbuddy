package com.autoapplicant.usecase.user;

import com.autoapplicant.adapter.security.JwtTokenProvider;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.user.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements RegisterUserUseCase, LoginUserUseCase,
        GetUserProfileUseCase, UpdateUserProfileUseCase, UpdatePreferencesUseCase {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserService(UserRepositoryPort userRepo, ProfileRepositoryPort profileRepo,
                       PreferencesRepositoryPort prefsRepo, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public User register(String email, String password, String fullName) {
        if (userRepo.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User(null, email, passwordEncoder.encode(password),
                null, null, UserRole.USER, false, null, null);
        User saved = userRepo.save(user);
        // Create initial profile
        Profile profile = new Profile(null, saved.id(), fullName, null, null,
                null, null, null, null, null, null,
                java.util.List.of(), java.util.List.of(), java.util.List.of(),
                null, null, "DKK", null, null, null, null);
        profileRepo.save(profile);
        return saved;
    }

    @Override
    public String login(String email, String password) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return jwtTokenProvider.generateToken(user.id(), user.email(), user.role());
    }

    @Override
    public Optional<Profile> getProfile(UUID userId) {
        return profileRepo.findByUserId(userId);
    }

    @Override
    public Profile updateProfile(UUID userId, Profile profile) {
        return profileRepo.save(new Profile(null, userId, profile.fullName(), profile.headline(),
                profile.summary(), profile.location(), profile.municipality(),
                profile.linkedinUrl(), profile.githubUrl(), profile.websiteUrl(),
                profile.yearsExperience(), profile.skills(), profile.technologies(), profile.languages(),
                profile.desiredSalaryMin(), profile.desiredSalaryMax(), profile.desiredCurrency(),
                profile.remotePreference(), profile.employmentTypePreference(), null, null));
    }

    @Override
    public UserPreferences updatePreferences(UUID userId, UserPreferences preferences) {
        return prefsRepo.save(new UserPreferences(null, userId,
                preferences.preferredLocations(), preferences.preferredMunicipalities(),
                preferences.positiveSignals(), preferences.negativeSignals(), preferences.excludedCompanies(),
                preferences.preferredRemoteTypes(), preferences.preferredEmploymentTypes(),
                preferences.preferredSeniority(), preferences.salaryMin(), preferences.salaryMax(),
                preferences.notificationEnabled(), preferences.notificationFrequency(), null, null));
    }
}
