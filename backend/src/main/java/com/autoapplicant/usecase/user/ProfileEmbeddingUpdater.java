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
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProjectRepositoryPort;
import com.autoapplicant.port.out.user.WorkExperienceRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
 *
 * <p>Refreshes for one user are serialised and each reads the profile as it is now, so rapid
 * saves cannot finish out of order and leave an older embedding stored over a newer one. A save
 * that arrives while a refresh is already waiting is dropped: the waiting one will read it, so a
 * burst of saves costs at most one embed call in flight and one queued.
 */
@Component
public class ProfileEmbeddingUpdater {

    private static final Logger log = LoggerFactory.getLogger(ProfileEmbeddingUpdater.class);
    // Striped rather than one lock per user so the set stays bounded; a collision only makes
    // two users' refreshes wait on each other.
    private static final int LOCK_STRIPES = 64;

    private final ReentrantLock[] userLocks = newLocks();
    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();
    private final ProfileRepositoryPort profileRepo;

    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    private final WorkExperienceRepositoryPort workExpRepo;
    private final ProjectRepositoryPort projectRepo;
    private final CertificationRepositoryPort certRepo;
    private final AiProviderPort aiProvider;
    private final ProfileSkillRepositoryPort profileSkillRepo;

    public ProfileEmbeddingUpdater(ProfileRepositoryPort profileRepo,
                                   ProfileEmbeddingRepositoryPort profileEmbeddingRepo,
                                   WorkExperienceRepositoryPort workExpRepo,
                                   ProjectRepositoryPort projectRepo,
                                   CertificationRepositoryPort certRepo,
                                   @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                                   ProfileSkillRepositoryPort profileSkillRepo) {
        this.profileRepo = profileRepo;
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
        if (!waiting.add(event.userId())) return;
        ReentrantLock lock = lockFor(event.userId());
        lock.lock();
        // Leave the waiting set once running, so a save made during this refresh queues another.
        waiting.remove(event.userId());
        try {
            recompute(event.userId());
        } catch (Exception e) {
            log.warn("Failed to compute profile embedding for user {}: {}",
                    event.userId(), e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void recompute(UUID userId) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        String profileText = profile == null ? "" : buildProfileText(profile, skillNames(userId),
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

    private ReentrantLock lockFor(UUID userId) {
        return userLocks[Math.floorMod(userId.hashCode(), LOCK_STRIPES)];
    }

    private static ReentrantLock[] newLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
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
        nullToEmpty(experience).forEach(w -> appendExperience(sb, w));
        nullToEmpty(projects).forEach(proj -> appendProject(sb, proj));
        nullToEmpty(certs).forEach(c -> appendCertification(sb, c));
        return sb.toString().trim();
    }

    private static void appendExperience(StringBuilder sb, WorkExperience w) {
        appendText(sb, w.title());
        appendText(sb, w.companyName());
        appendText(sb, w.description());
        appendWords(sb, w.technologies());
    }

    private static void appendProject(StringBuilder sb, Project proj) {
        appendText(sb, proj.name());
        appendText(sb, proj.description());
        appendWords(sb, proj.technologies());
    }

    private static void appendCertification(StringBuilder sb, Certification c) {
        appendText(sb, c.name());
        appendText(sb, c.issuer());
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
