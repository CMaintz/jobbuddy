package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.DanishMunicipalityCoordinates;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService implements GetRecommendationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    // Behavioral adjustments
    private static final int LIKE_BOOST     = 10;
    private static final int DISLIKE_PENALTY = -20;

    // Preference score bonuses (soft)
    private static final int REMOTE_MATCH_BONUS      = 8;
    private static final int SENIORITY_MATCH_BONUS   = 8;
    private static final int LOCATION_MATCH_BONUS    = 8;
    private static final int SALARY_IN_RANGE_BONUS   = 6;
    private static final int POSITIVE_SIGNAL_BONUS   = 4;   // per signal, capped
    private static final int NEGATIVE_SIGNAL_PENALTY = -6;  // per signal, capped
    private static final int MAX_SIGNAL_BONUS        = 12;
    private static final int MAX_SIGNAL_PENALTY      = -18;

    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final AiProviderPort aiProvider;
    private final IgnoredJobRepositoryPort ignoredJobRepo;
    private final RecommendationFeedbackRepositoryPort feedbackRepo;

    public MatchingService(JobEmbeddingRepositoryPort embeddingRepo,
                           JobRepositoryPort jobRepo,
                           ProfileRepositoryPort profileRepo,
                           PreferencesRepositoryPort prefsRepo,
                           AiProviderPort aiProvider,
                           IgnoredJobRepositoryPort ignoredJobRepo,
                           RecommendationFeedbackRepositoryPort feedbackRepo) {
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
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

            UserPreferences prefs = prefsRepo.findByUserId(userId).orElse(null);

            Set<UUID> ignoredIds    = ignoredJobRepo.findJobIdsByUserId(userId);
            Map<UUID, FeedbackType> feedbackMap = buildFeedbackMap(userId);
            Set<UUID> hiddenIds = feedbackMap.entrySet().stream()
                    .filter(e -> e.getValue() == FeedbackType.HIDE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            float[] userEmbedding = aiProvider.embed(profileText);
            // Fetch extra candidates so we have room to discard hard-constraint failures
            List<UUID> nearestJobIds = embeddingRepo.findNearestNeighborJobIds(userEmbedding, limit * 5);

            Set<String> profileSkills = normalizedSet(profile.skills());
            Set<String> profileTech   = normalizedSet(profile.technologies());

            return nearestJobIds.stream()
                    .filter(id -> !ignoredIds.contains(id) && !hiddenIds.contains(id))
                    .flatMap(jobId -> jobRepo.findById(jobId).stream())
                    .filter(job -> passesHardConstraints(job, prefs))
                    .limit(limit * 2L)
                    .map(job -> buildMatchResult(job, userId, profileSkills, profileTech, feedbackMap, prefs))
                    .sorted(Comparator.comparingInt(MatchResult::totalScore).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Recommendation generation failed for user {}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    // ── Hard constraint filtering ─────────────────────────────────────────────

    /**
     * Returns false if the job violates a preference the user has explicitly set.
     * We only filter on fields that the job itself has populated — a job without
     * a known remoteType is never excluded on remote grounds.
     */
    private boolean passesHardConstraints(Job job, UserPreferences prefs) {
        if (prefs == null) return true;

        // Excluded companies — substring match, case-insensitive
        if (notEmpty(prefs.excludedCompanies()) && job.companyName() != null) {
            String lowerCompany = job.companyName().toLowerCase();
            if (prefs.excludedCompanies().stream()
                    .anyMatch(exc -> lowerCompany.contains(exc.toLowerCase().trim()))) {
                return false;
            }
        }

        // Remote type — only enforce when both preference and job type are known
        if (notEmpty(prefs.preferredRemoteTypes()) && job.remoteType() != null) {
            if (!prefs.preferredRemoteTypes().contains(job.remoteType().name())) {
                return false;
            }
        }

        // Employment type
        if (notEmpty(prefs.preferredEmploymentTypes()) && job.employmentType() != null) {
            if (!prefs.preferredEmploymentTypes().contains(job.employmentType().name())) {
                return false;
            }
        }

        // Seniority
        if (notEmpty(prefs.preferredSeniority()) && job.seniority() != null) {
            if (!prefs.preferredSeniority().contains(job.seniority().name())) {
                return false;
            }
        }

        // Commute distance — only applies when maxCommuteKm is set, job has a municipality,
        // and the job is not fully remote (remote jobs have no physical commute constraint)
        if (prefs.maxCommuteKm() != null && job.municipality() != null
                && job.remoteType() != RemoteType.REMOTE) {
            double[] jobCoords = DanishMunicipalityCoordinates.get(job.municipality());
            if (jobCoords != null && !isWithinCommuteDistance(prefs, jobCoords, prefs.maxCommuteKm())) {
                return false;
            }
            // If coordinates unknown, we skip the filter (benefit of the doubt)
        }

        return true;
    }

    /**
     * Returns true if the job is within maxCommuteKm of any of the user's preferred
     * municipalities or preferred locations.
     */
    private boolean isWithinCommuteDistance(UserPreferences prefs, double[] jobCoords, int maxKm) {
        // Try preferred municipalities first (more specific), then fallback to locations
        List<String> homePoints = notEmpty(prefs.preferredMunicipalities())
                ? prefs.preferredMunicipalities()
                : (notEmpty(prefs.preferredLocations()) ? prefs.preferredLocations() : List.of());

        if (homePoints.isEmpty()) return true; // no reference point configured — skip filter

        for (String home : homePoints) {
            double[] homeCoords = DanishMunicipalityCoordinates.get(home);
            if (homeCoords != null) {
                double km = DanishMunicipalityCoordinates.haversineKm(
                        homeCoords[0], homeCoords[1], jobCoords[0], jobCoords[1]);
                if (km <= maxKm) return true;
            }
        }
        return false;
    }

    // ── Score building ────────────────────────────────────────────────────────

    private MatchResult buildMatchResult(Job job, UUID userId,
                                         Set<String> profileSkills, Set<String> profileTech,
                                         Map<UUID, FeedbackType> feedbackMap,
                                         UserPreferences prefs) {
        List<String> reasons = new ArrayList<>();

        // ── Content relevance (0-40) ────────────────────────────────────────
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

        int contentScore = Math.min(matchedTech.size() * 6, 24) + (int) Math.min(skillMatches * 4, 16);

        // ── Preference bonuses (0-30) ────────────────────────────────────────
        int prefScore = 0;

        if (prefs != null) {
            // Remote type match
            if (notEmpty(prefs.preferredRemoteTypes()) && job.remoteType() != null) {
                if (prefs.preferredRemoteTypes().contains(job.remoteType().name())) {
                    prefScore += REMOTE_MATCH_BONUS;
                    reasons.add("Matches your remote preference (" + job.remoteType().name().toLowerCase().replace('_', ' ') + ")");
                }
            } else if (job.remoteType() != null) {
                // No preference set, just report what it is
                reasons.add("Remote: " + job.remoteType().name().toLowerCase().replace('_', ' '));
            }

            // Seniority match
            if (notEmpty(prefs.preferredSeniority()) && job.seniority() != null) {
                if (prefs.preferredSeniority().contains(job.seniority().name())) {
                    prefScore += SENIORITY_MATCH_BONUS;
                    reasons.add("Matches your seniority preference (" + job.seniority().name().toLowerCase() + ")");
                }
            } else if (job.seniority() != null) {
                reasons.add("Seniority: " + job.seniority().name().toLowerCase());
            }

            // Location / municipality match
            if (notEmpty(prefs.preferredMunicipalities()) && job.municipality() != null) {
                String jobMuni = job.municipality().toLowerCase();
                if (prefs.preferredMunicipalities().stream()
                        .anyMatch(m -> jobMuni.contains(m.toLowerCase().trim()))) {
                    prefScore += LOCATION_MATCH_BONUS;
                    reasons.add("Located in " + job.municipality());
                } else if (prefs.maxCommuteKm() != null) {
                    // Show distance from nearest preferred location
                    double[] jobCoords = DanishMunicipalityCoordinates.get(job.municipality());
                    if (jobCoords != null) {
                        double minKm = prefs.preferredMunicipalities().stream()
                                .mapToDouble(m -> {
                                    double[] c = DanishMunicipalityCoordinates.get(m);
                                    return c != null
                                            ? DanishMunicipalityCoordinates.haversineKm(c[0], c[1], jobCoords[0], jobCoords[1])
                                            : Double.MAX_VALUE;
                                }).min().orElse(Double.MAX_VALUE);
                        if (minKm < Double.MAX_VALUE) {
                            reasons.add(String.format("~%.0f km from your location", minKm));
                        }
                    }
                }
            } else if (notEmpty(prefs.preferredLocations()) && job.location() != null) {
                String jobLoc = job.location().toLowerCase();
                if (prefs.preferredLocations().stream()
                        .anyMatch(l -> jobLoc.contains(l.toLowerCase().trim()))) {
                    prefScore += LOCATION_MATCH_BONUS;
                    reasons.add("Located in " + job.location());
                }
            }

            // Salary in range (only if job has salary data)
            if (job.salaryMin() != null || job.salaryMax() != null) {
                boolean inRange = isSalaryInRange(job, prefs);
                if (inRange) {
                    prefScore += SALARY_IN_RANGE_BONUS;
                    reasons.add("Salary within your range");
                } else if (prefs.salaryMin() != null || prefs.salaryMax() != null) {
                    // Job has salary data but it's outside range — soft penalty
                    prefScore -= SALARY_IN_RANGE_BONUS;
                    reasons.add("Salary may be outside your range");
                }
            }

            // Positive/negative keyword signals in description
            String descLower = job.descriptionClean() != null
                    ? job.descriptionClean().toLowerCase()
                    : (job.descriptionRaw() != null ? job.descriptionRaw().toLowerCase() : "");

            if (!descLower.isBlank()) {
                int signalBonus = 0;
                if (notEmpty(prefs.positiveSignals())) {
                    long posHits = prefs.positiveSignals().stream()
                            .filter(s -> descLower.contains(s.toLowerCase().trim()))
                            .count();
                    if (posHits > 0) {
                        signalBonus += Math.min((int) posHits * POSITIVE_SIGNAL_BONUS, MAX_SIGNAL_BONUS);
                        reasons.add(posHits + " positive signal" + (posHits > 1 ? "s" : "") + " found");
                    }
                }
                if (notEmpty(prefs.negativeSignals())) {
                    long negHits = prefs.negativeSignals().stream()
                            .filter(s -> descLower.contains(s.toLowerCase().trim()))
                            .count();
                    if (negHits > 0) {
                        signalBonus += Math.max((int) negHits * NEGATIVE_SIGNAL_PENALTY, MAX_SIGNAL_PENALTY);
                        reasons.add(negHits + " negative signal" + (negHits > 1 ? "s" : "") + " in description");
                    }
                }
                prefScore += signalBonus;
            }

        } else {
            // No preferences stored — just report job attributes
            if (job.remoteType() != null) {
                reasons.add("Remote: " + job.remoteType().name().toLowerCase().replace('_', ' '));
            }
            if (job.seniority() != null) {
                reasons.add("Seniority: " + job.seniority().name().toLowerCase());
            }
        }

        // ── Behavioral adjustment ────────────────────────────────────────────
        FeedbackType feedback = feedbackMap.get(job.id());
        int behavioralBoost = 0;
        if (feedback == FeedbackType.LIKE || feedback == FeedbackType.MORE_LIKE_THIS) {
            behavioralBoost = LIKE_BOOST;
            reasons.add("Previously liked");
        } else if (feedback == FeedbackType.DISLIKE || feedback == FeedbackType.FEWER_LIKE_THIS) {
            behavioralBoost = DISLIKE_PENALTY;
        }

        // ── Final score ──────────────────────────────────────────────────────
        // Base 40 + content (0-40) + pref bonuses (variable) + behavioral
        int total = Math.min(Math.max(40 + contentScore + prefScore + behavioralBoost, 0), 100);

        double semanticScore  = contentScore / 40.0;
        double behavioralScore = (behavioralBoost + 20) / 40.0; // normalise to 0-1

        return new MatchResult(job.id(), userId, job, true,
                semanticScore, behavioralScore, total, MatchLabel.fromScore(total), reasons);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isSalaryInRange(Job job, UserPreferences prefs) {
        Integer jobMin = job.salaryMin();
        Integer jobMax = job.salaryMax();
        Integer prefMin = prefs.salaryMin();
        Integer prefMax = prefs.salaryMax();

        if (prefMin == null && prefMax == null) return true;

        // Use the midpoint of the job's range if both ends available, else whichever end exists
        int jobPoint = jobMin != null && jobMax != null ? (jobMin + jobMax) / 2
                : jobMin != null ? jobMin : jobMax;

        if (prefMin != null && jobPoint < prefMin) return false;
        if (prefMax != null && jobPoint > prefMax) return false;
        return true;
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

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }
}
