package com.autoapplicant.usecase.user;

import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.domain.user.ProfileSavedEvent;
import com.autoapplicant.domain.user.Project;
import com.autoapplicant.domain.user.WorkExperience;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.CertificationRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recomputes a user's profile embedding off the request thread whenever their profile is saved.
 *
 * <p>A bean of its own so the {@code @Async} proxy actually applies: the old self-invoked async
 * method on the saving service ran inline and blocked the request on the embedding call. Runs
 * after commit when the save happens inside a transaction, and right away when it does not.
 */
@Component
public class ProfileEmbeddingUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProfileEmbeddingUpdater.class);

    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final CertificationRepositoryPort certRepo;
    private final AiProviderPort aiProvider;
    private final ProfileSkillRepositoryPort profileSkillRepo;

    public ProfileEmbeddingUpdater(ProfileEmbeddingRepositoryPort profileEmbeddingRepo,
                                   WorkExperienceRepositoryPort workExpRepo,
                                   ProjectRepositoryPort projectRepo,
                                   CertificationRepositoryPort certRepo,
                                   @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                                   ProfileSkillRepositoryPort profileSkillRepo) {
        this.profileEmbeddingRepo = profileEmbeddingRepo;
        this.workExpRepo = workExpRepo;
        this.projectRepo = projectRepo;
        this.certRepo = certRepo;
        this.aiProvider = aiProvider;
        this.profileSkillRepo = profileSkillRepo;
    }

    @Async("userAiTaskExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onProfileSaved(ProfileSavedEvent event) {
        try {
            recompute(event.userId(), event.profile());
        } catch (Exception e) {
            log.warn("Failed to compute profile embedding for user {}: {}",
                    event.userId(), e.getMessage());
        }
    }

    private void recompute(UUID userId, Profile profile) {
        String profileText = buildProfileText(profile, skillNames(userId),
                workExpRepo.findByUserId(userId),
                projectRepo.findByUserId(userId),
                certRepo.findByUserId(userId));
        if (profileText.isBlank()) {
            profileEmbeddingRepo.deleteByUserId(userId);
            return;
        }
        float[] vector = aiProvider.embed(profileText);
        profileEmbeddingRepo.save(new ProfileEmbedding(
                null, userId, vector, aiProvider.embeddingModelName(), Instant.now()));
        log.debug("Updated profile embedding for user {}", userId);
    }

    // Skill names come from the profile's skill rows now. The embedding is what makes a
    // posting find this candidate at all, so dropping them would quietly cost recall.
    private List<String> skillNames(UUID userId) {
        return profileSkillRepo.findByUserId(userId).stream()
                .map(ProfileSkill::skillName)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Builds a rich text representation of the user's full profile for embedding.
     * Includes headline, summary, skills, work experience titles/descriptions,
     * project names, and certifications for better semantic matching quality.
     */
    private static String buildProfileText(Profile p, List<String> skillNames, List<WorkExperience> experience,
                                           List<Project> projects, List<Certification> certs) {
        StringBuilder sb = new StringBuilder();
        appendText(sb, p.headline());
        appendText(sb, p.summary());
        appendWords(sb, skillNames);
        for (WorkExperience w : nullToEmpty(experience)) {
            appendText(sb, w.title());
            appendText(sb, w.companyName());
            appendText(sb, w.description());
            appendWords(sb, w.technologies());
        }
        for (Project proj : nullToEmpty(projects)) {
            appendText(sb, proj.name());
            appendText(sb, proj.description());
            appendWords(sb, proj.technologies());
        }
        for (Certification c : nullToEmpty(certs)) {
            appendText(sb, c.name());
            appendText(sb, c.issuer());
        }
        return sb.toString().trim();
    }

    private static void appendText(StringBuilder sb, String text) {
        if (text != null) sb.append(text).append(' ');
    }

    private static void appendWords(StringBuilder sb, List<String> words) {
        if (words != null && !words.isEmpty()) sb.append(String.join(" ", words)).append(' ');
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list != null ? list : List.of();
    }
}
