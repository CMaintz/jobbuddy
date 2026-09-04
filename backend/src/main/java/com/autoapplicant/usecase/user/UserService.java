package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.in.user.*;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.user.*;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.domain.skill.ProfileSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements GetUserProfileUseCase, UpdateUserProfileUseCase, UpdatePreferencesUseCase, CompleteOnboardingUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final CertificationRepositoryPort certRepo;
    private final AiProviderPort aiProvider;
    private final ProfileSkillRepositoryPort profileSkillRepo;

    public UserService(UserRepositoryPort userRepo,
                       ProfileRepositoryPort profileRepo,
                       PreferencesRepositoryPort prefsRepo,
                       ProfileEmbeddingRepositoryPort profileEmbeddingRepo,
                       WorkExperienceRepositoryPort workExpRepo,
                       ProjectRepositoryPort projectRepo,
                       CertificationRepositoryPort certRepo,
                       @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                       ProfileSkillRepositoryPort profileSkillRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.profileEmbeddingRepo = profileEmbeddingRepo;
        this.workExpRepo = workExpRepo;
        this.projectRepo = projectRepo;
        this.certRepo = certRepo;
        this.aiProvider = aiProvider;
        this.profileSkillRepo = profileSkillRepo;
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
        recomputeProfileEmbeddingAsync(userId, saved);
        return saved;
    }

    private static <T> T merged(T incoming, Profile existing, java.util.function.Function<Profile, T> getter) {
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

    @Async("userAiTaskExecutor")
    protected void recomputeProfileEmbeddingAsync(UUID userId, Profile profile) {
        try {
            List<WorkExperience> experience = workExpRepo.findByUserId(userId);
            List<Project> projects = projectRepo.findByUserId(userId);
            List<Certification> certs = certRepo.findByUserId(userId);

            // Skill names come from the profile's skill rows now. The embedding is what makes a
            // posting find this candidate at all, so dropping them would quietly cost recall.
            List<String> skillNames = profileSkillRepo.findByUserId(userId).stream()
                    .map(ProfileSkill::skillName)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            String profileText = buildProfileText(profile, skillNames, experience, projects, certs);
            if (profileText.isBlank()) {
                profileEmbeddingRepo.deleteByUserId(userId);
                return;
            }
            float[] vector = aiProvider.embed(profileText);
            profileEmbeddingRepo.save(new ProfileEmbedding(
                    null, userId, vector, aiProvider.embeddingModelName(), Instant.now()));
            log.debug("Updated profile embedding for user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to compute profile embedding for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Builds a rich text representation of the user's full profile for embedding.
     * Includes headline, summary, skills, work experience titles/descriptions,
     * project names, and certifications for better semantic matching quality.
     */
    static String buildProfileText(Profile p, List<String> skillNames, List<WorkExperience> experience,
                                   List<Project> projects, List<Certification> certs) {
        StringBuilder sb = new StringBuilder();
        if (p.headline() != null) sb.append(p.headline()).append(' ');
        if (p.summary() != null) sb.append(p.summary()).append(' ');
        if (skillNames != null && !skillNames.isEmpty()) sb.append(String.join(" ", skillNames)).append(' ');

        if (experience != null) {
            for (WorkExperience w : experience) {
                if (w.title() != null) sb.append(w.title()).append(' ');
                if (w.companyName() != null) sb.append(w.companyName()).append(' ');
                if (w.description() != null) sb.append(w.description()).append(' ');
                if (w.technologies() != null) sb.append(String.join(" ", w.technologies())).append(' ');
            }
        }

        if (projects != null) {
            for (Project proj : projects) {
                if (proj.name() != null) sb.append(proj.name()).append(' ');
                if (proj.description() != null) sb.append(proj.description()).append(' ');
                if (proj.technologies() != null) sb.append(String.join(" ", proj.technologies())).append(' ');
            }
        }

        if (certs != null) {
            for (Certification c : certs) {
                if (c.name() != null) sb.append(c.name()).append(' ');
                if (c.issuer() != null) sb.append(c.issuer()).append(' ');
            }
        }

        return sb.toString().trim();
    }

    /**
     * Simple profile text for MatchingService fallback (when no cached embedding exists).
     * Skills come from the caller, which has already loaded them.
     */
    static String buildProfileText(Profile p, List<String> skillNames) {
        return buildProfileText(p, skillNames, List.of(), List.of(), List.of());
    }
}
