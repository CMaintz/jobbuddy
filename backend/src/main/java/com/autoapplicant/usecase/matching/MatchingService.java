package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService implements GetRecommendationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private static final int LIKE_BOOST = 10;
    private static final int DISLIKE_PENALTY = -20;

    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;
    private final ProfileRepositoryPort profileRepo;
    private final AiProviderPort aiProvider;
    private final IgnoredJobRepositoryPort ignoredJobRepo;
    private final RecommendationFeedbackRepositoryPort feedbackRepo;

    public MatchingService(JobEmbeddingRepositoryPort embeddingRepo, JobRepositoryPort jobRepo,
                           ProfileRepositoryPort profileRepo, AiProviderPort aiProvider,
                           IgnoredJobRepositoryPort ignoredJobRepo,
                           RecommendationFeedbackRepositoryPort feedbackRepo) {
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
        this.profileRepo = profileRepo;
        this.aiProvider = aiProvider;
        this.ignoredJobRepo = ignoredJobRepo;
        this.feedbackRepo = feedbackRepo;
    }

    @Override
    public List<MatchResult> getRecommendations(UUID userId, int limit) {
        try {
            Profile profile = profileRepo.findByUserId(userId).orElse(null);
            if (profile == null) return List.of();

            String profileText = buildProfileText(profile);
            if (profileText.isBlank()) return List.of();

            Set<UUID> ignoredIds = ignoredJobRepo.findJobIdsByUserId(userId);
            Map<UUID, FeedbackType> feedbackMap = buildFeedbackMap(userId);
            Set<UUID> hiddenIds = feedbackMap.entrySet().stream()
                    .filter(e -> e.getValue() == FeedbackType.HIDE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            float[] userEmbedding = aiProvider.embed(profileText);
            List<UUID> nearestJobIds = embeddingRepo.findNearestNeighborJobIds(userEmbedding, limit * 3);

            Set<String> profileSkills = normalizedSet(profile.skills());
            Set<String> profileTech = normalizedSet(profile.technologies());

            return nearestJobIds.stream()
                    .filter(id -> !ignoredIds.contains(id) && !hiddenIds.contains(id))
                    .limit(limit * 2L)
                    .flatMap(jobId -> jobRepo.findById(jobId).stream())
                    .map(job -> buildMatchResult(job, userId, profileSkills, profileTech, feedbackMap))
                    .sorted(Comparator.comparingInt(MatchResult::totalScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Recommendation generation failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private MatchResult buildMatchResult(Job job, UUID userId,
                                         Set<String> profileSkills, Set<String> profileTech,
                                         Map<UUID, FeedbackType> feedbackMap) {
        List<String> reasons = new ArrayList<>();

        Set<String> jobTech = normalizedSet(job.technologies());
        List<String> matchedTech = profileTech.stream()
                .filter(jobTech::contains).sorted().limit(5).toList();
        if (!matchedTech.isEmpty()) {
            reasons.add("Technology overlap: " + String.join(", ", matchedTech));
        }

        Set<String> jobSkills = normalizedSet(job.skills());
        long skillMatches = profileSkills.stream().filter(jobSkills::contains).count();
        if (skillMatches > 0) {
            reasons.add(skillMatches + " matching skill" + (skillMatches > 1 ? "s" : ""));
        }

        if (job.remoteType() != null) {
            reasons.add("Remote: " + job.remoteType().name().toLowerCase().replace('_', ' '));
        }

        if (job.seniority() != null) {
            reasons.add("Seniority: " + job.seniority().name().toLowerCase().replace('_', ' '));
        }

        int base = 50 + Math.min(matchedTech.size() * 8, 30) + (int) Math.min(skillMatches * 5, 20);

        FeedbackType feedback = feedbackMap.get(job.id());
        int behavioralBoost = 0;
        if (feedback == FeedbackType.LIKE || feedback == FeedbackType.MORE_LIKE_THIS) {
            behavioralBoost = LIKE_BOOST;
            reasons.add("Previously liked");
        } else if (feedback == FeedbackType.DISLIKE || feedback == FeedbackType.FEWER_LIKE_THIS) {
            behavioralBoost = DISLIKE_PENALTY;
        }

        int score = Math.min(Math.max(base + behavioralBoost, 0), 100);

        return new MatchResult(job.id(), userId, job, true,
                score / 100.0, 0.0, score, MatchLabel.fromScore(score), reasons);
    }

    private Map<UUID, FeedbackType> buildFeedbackMap(UUID userId) {
        return feedbackRepo.findByUserId(userId).stream()
                .collect(Collectors.toMap(RecommendationFeedback::jobId,
                        RecommendationFeedback::feedbackType,
                        (a, b) -> b));
    }

    private static String buildProfileText(Profile p) {
        StringBuilder sb = new StringBuilder();
        if (p.headline() != null) sb.append(p.headline()).append(' ');
        if (p.summary() != null) sb.append(p.summary()).append(' ');
        if (p.skills() != null) sb.append(String.join(" ", p.skills())).append(' ');
        if (p.technologies() != null) sb.append(String.join(" ", p.technologies()));
        return sb.toString().trim();
    }

    private static Set<String> normalizedSet(List<String> list) {
        if (list == null) return Set.of();
        return list.stream().map(s -> s.toLowerCase().trim()).collect(Collectors.toSet());
    }
}
