package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.DanishMunicipalityCoordinates;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobCategory;
import com.autoapplicant.domain.job.RemoteType;
import com.autoapplicant.domain.matching.FeedbackType;
import com.autoapplicant.domain.matching.MatchLabel;
import com.autoapplicant.domain.matching.MatchResult;
import com.autoapplicant.domain.matching.RecommendationFeedback;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfileEmbedding;
import com.autoapplicant.domain.user.UserPreferences;
import com.autoapplicant.port.in.job.GetRecommendationsUseCase;
import com.autoapplicant.port.out.ai.AiProviderPort;
import org.springframework.beans.factory.annotation.Qualifier;
import com.autoapplicant.port.out.job.IgnoredJobRepositoryPort;
import com.autoapplicant.port.out.job.JobEmbeddingRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.matching.RecommendationFeedbackRepositoryPort;
import com.autoapplicant.port.out.user.PreferencesRepositoryPort;
import com.autoapplicant.port.out.user.ProfileEmbeddingRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.domain.skill.ProfileSkill;
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

    // ── Score budget ─────────────────────────────────────────────────────────
    // Preferences decide which jobs are *eligible* (see passesHardConstraints); skills decide
    // how the eligible ones *rank*. So the skill budget is the largest term, and a preference
    // that already acted as a hard filter no longer earns a bonus — every surviving job would
    // score it identically, which shifts the whole list rather than ordering it.
    private static final int BASE_SCORE          = 25;
    private static final int MAX_SKILL_SCORE     = 55;
    private static final int REQUIRED_BUDGET     = 34;  // coverage of what the posting demands
    private static final int PREFERRED_BUDGET    = 9;   // coverage of what it merely welcomes
    private static final int TECH_OVERLAP_BUDGET = 12;  // overlap the posting did not tier
    private static final int MAX_MISSING_REQUIRED_PENALTY = -22;
    private static final int MISSING_REQUIRED_PENALTY_EACH = -6;

    // A partially-held required skill still counts, just not fully: a skill listed at BEGINNER
    // and never used in production is not the same evidence as one used in production for years.
    private static final double WEAK_PROFICIENCY_CREDIT = 0.5;

    /** Retrieval multiplier. Raised when a hard filter will discard most of what comes back. */
    private static final int CANDIDATE_MULTIPLIER          = 5;
    private static final int FILTERED_CANDIDATE_MULTIPLIER = 15;

    private final JobEmbeddingRepositoryPort embeddingRepo;
    private final JobRepositoryPort jobRepo;
    private final ProfileRepositoryPort profileRepo;
    private final PreferencesRepositoryPort prefsRepo;
    private final AiProviderPort aiProvider;
    private final IgnoredJobRepositoryPort ignoredJobRepo;
    private final RecommendationFeedbackRepositoryPort feedbackRepo;
    private final ProfileEmbeddingRepositoryPort profileEmbeddingRepo;
    private final ProfileSkillRepositoryPort profileSkillRepo;

    public MatchingService(JobEmbeddingRepositoryPort embeddingRepo,
                           JobRepositoryPort jobRepo,
                           ProfileRepositoryPort profileRepo,
                           PreferencesRepositoryPort prefsRepo,
                           @Qualifier("enrichmentAiProvider") AiProviderPort aiProvider,
                           IgnoredJobRepositoryPort ignoredJobRepo,
                           RecommendationFeedbackRepositoryPort feedbackRepo,
                           ProfileEmbeddingRepositoryPort profileEmbeddingRepo,
                           ProfileSkillRepositoryPort profileSkillRepo) {
        this.embeddingRepo = embeddingRepo;
        this.jobRepo = jobRepo;
        this.profileRepo = profileRepo;
        this.prefsRepo = prefsRepo;
        this.aiProvider = aiProvider;
        this.ignoredJobRepo = ignoredJobRepo;
        this.feedbackRepo = feedbackRepo;
        this.profileEmbeddingRepo = profileEmbeddingRepo;
        this.profileSkillRepo = profileSkillRepo;
    }

    @Override
    public List<MatchResult> getRecommendations(UUID userId, int limit) {
        try {
            Profile profile = profileRepo.findByUserId(userId).orElse(null);
            if (profile == null) return List.of();

            // One load of the profile's skills, feeding the embedding text, the fit scoring and
            // the proficiency weighting alike — they are the same skills.
            List<ProfileSkill> profileSkillRows = profileSkillRepo.findByUserId(userId);
            String profileText = buildProfileText(profile, profileSkillRows);
            if (profileText.isBlank()) return List.of();

            UserPreferences prefs = prefsRepo.findByUserId(userId).orElse(null);

            Set<UUID> ignoredIds    = ignoredJobRepo.findJobIdsByUserId(userId);
            Map<UUID, FeedbackType> feedbackMap = buildFeedbackMap(userId);
            Set<UUID> hiddenIds = feedbackMap.entrySet().stream()
                    .filter(e -> e.getValue() == FeedbackType.HIDE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // Use cached profile embedding when available; fall back to on-the-fly computation
            float[] userEmbedding = profileEmbeddingRepo.findByUserId(userId)
                    .map(ProfileEmbedding::embedding)
                    .orElseGet(() -> aiProvider.embed(profileText));
            // Fetch extra candidates so we have room to discard hard-constraint failures.
            // A commute radius is the filter that discards most: nationwide postings are mostly
            // outside it, so at the normal multiplier the radius quietly starves the feed rather
            // than filtering it. Widen the pool instead of returning three jobs.
            int multiplier = prefs != null && prefs.maxCommuteKm() != null
                    ? FILTERED_CANDIDATE_MULTIPLIER : CANDIDATE_MULTIPLIER;
            List<UUID> nearestJobIds = embeddingRepo.findNearestNeighborJobIds(userEmbedding, limit * multiplier);

            // Filter out ignored/hidden IDs before hitting the DB
            List<UUID> candidateIds = nearestJobIds.stream()
                    .filter(id -> !ignoredIds.contains(id) && !hiddenIds.contains(id))
                    .toList();

            // Batch-fetch all candidate jobs in a single query instead of N+1
            Map<UUID, Job> jobMap = jobRepo.findByIds(candidateIds).stream()
                    .collect(Collectors.toMap(Job::id, j -> j));

            Set<String> held = profileSkillRows.stream()
                    .map(ProfileSkill::skillName)
                    .filter(Objects::nonNull)
                    .map(name -> name.toLowerCase().trim())
                    .collect(Collectors.toSet());
            Map<String, Double> proficiencyCredit = buildProficiencyCredit(profileSkillRows);

            return candidateIds.stream()
                    .map(jobMap::get)
                    .filter(Objects::nonNull)
                    .filter(job -> passesHardConstraints(job, prefs))
                    .limit(limit * 2L)
                    .map(job -> buildMatchResult(job, userId, held, proficiencyCredit, feedbackMap, prefs))
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

        // Industry hard constraint — jobs with null/OTHER category are never filtered out
        if (notEmpty(prefs.preferredIndustries())
                && job.jobCategory() != null
                && job.jobCategory() != JobCategory.OTHER) {
            if (!prefs.preferredIndustries().contains(job.jobCategory().name())) return false;
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
                                         Set<String> held,
                                         Map<String, Double> proficiencyCredit,
                                         Map<UUID, FeedbackType> feedbackMap,
                                         UserPreferences prefs) {
        List<String> reasons = new ArrayList<>();

        // ── Skill fit (0-55) ─────────────────────────────────────────────────
        int skillScore = scoreSkillFit(job, held, proficiencyCredit, reasons);

        // ── Preference bonuses ───────────────────────────────────────────────
        int prefScore = 0;

        if (prefs != null) {
            // Remote type. When a preference is set it already ran as a hard constraint, so every
            // job that reached here matches it — awarding a bonus would add the same 8 points to
            // every row and order nothing. Report it, don't score it. A job whose remote type is
            // unknown slipped the filter, so it is the one case still worth scoring.
            if (job.remoteType() != null) {
                boolean filteredOnRemote = notEmpty(prefs.preferredRemoteTypes());
                if (filteredOnRemote) {
                    reasons.add("Matches your remote preference (" + job.remoteType().name().toLowerCase().replace('_', ' ') + ")");
                } else {
                    reasons.add("Remote: " + job.remoteType().name().toLowerCase().replace('_', ' '));
                }
            } else if (notEmpty(prefs.preferredRemoteTypes())) {
                reasons.add("Remote arrangement not stated");
            }

            // Seniority — same reasoning as remote type.
            if (job.seniority() != null) {
                if (notEmpty(prefs.preferredSeniority())) {
                    reasons.add("Matches your seniority preference (" + job.seniority().name().toLowerCase() + ")");
                } else {
                    reasons.add("Seniority: " + job.seniority().name().toLowerCase());
                }
            }

            // Location. Unlike remote type, the radius filter is a band, not a point: everything
            // inside it survived, but a job 5 km away and one 45 km away are not the same
            // commute. So this bonus stays, graded by distance, and it is the one preference
            // that still orders the surviving list.
            if (notEmpty(prefs.preferredMunicipalities()) && job.municipality() != null) {
                String jobMuni = job.municipality().toLowerCase();
                if (prefs.preferredMunicipalities().stream()
                        .anyMatch(m -> jobMuni.contains(m.toLowerCase().trim()))) {
                    prefScore += LOCATION_MATCH_BONUS;
                    reasons.add("Located in " + job.municipality());
                } else if (job.remoteType() == RemoteType.REMOTE) {
                    // No commute to grade — fully remote is as good as next door.
                    prefScore += LOCATION_MATCH_BONUS;
                    reasons.add("Fully remote — location is not a constraint");
                } else {
                    double minKm = distanceToNearestPreferred(prefs, job);
                    if (minKm < Double.MAX_VALUE) {
                        reasons.add(String.format("~%.0f km from your location", minKm));
                        prefScore += gradedProximityBonus(minKm, prefs.maxCommuteKm());
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
        // Base 25 + skill fit (0-55) + pref bonuses (variable) + behavioral
        int total = Math.min(Math.max(BASE_SCORE + skillScore + prefScore + behavioralBoost, 0), 100);

        double semanticScore  = skillScore / (double) MAX_SKILL_SCORE;
        double behavioralScore = (behavioralBoost + 20) / 40.0; // normalise to 0-1

        return new MatchResult(job.id(), userId, job, true,
                semanticScore, behavioralScore, total, MatchLabel.fromScore(total), reasons);
    }

    // ── Skill fit ─────────────────────────────────────────────────────────────

    /**
     * How well the profile covers what the posting asks for, on a 0-{@value #MAX_SKILL_SCORE}
     * scale, appending its findings to {@code reasons}.
     *
     * <p>The posting's asks come in two tiers because Danish postings write them in two tiers:
     * "du skal have" is a demand, "det er en fordel" is a wish. Scoring them alike is what made
     * a nice-to-have you happen to hold worth as much as a must-have you lack — and made lacking
     * a must-have cost nothing at all, since a missing skill simply scored zero.
     *
     * <p>So: required coverage carries most of the budget AND a shortfall penalty, preferred
     * coverage carries a small bonus and no penalty, and untiered technology overlap carries the
     * rest. A job enriched before the tiers existed has neither list, and falls back to that
     * untiered overlap alone — scaled up so old rows are not silently buried under new ones.
     */
    private int scoreSkillFit(Job job, Set<String> held,
                              Map<String, Double> proficiencyCredit, List<String> reasons) {
        List<String> required  = job.requiredSkills()  != null ? job.requiredSkills()  : List.of();
        List<String> preferred = job.preferredSkills() != null ? job.preferredSkills() : List.of();

        if (required.isEmpty() && preferred.isEmpty()) {
            return scoreUntiered(job, held, reasons, MAX_SKILL_SCORE);
        }

        int score = 0;

        if (!required.isEmpty()) {
            List<String> missing = new ArrayList<>();
            double credit = 0;
            for (String ask : required) {
                double c = creditFor(ask, held, proficiencyCredit);
                credit += c;
                if (c == 0) missing.add(ask);
            }
            double coverage = credit / required.size();
            score += (int) Math.round(coverage * REQUIRED_BUDGET);

            int covered = required.size() - missing.size();
            reasons.add(covered + "/" + required.size() + " required skill"
                    + (required.size() > 1 ? "s" : "") + " covered");
            if (!missing.isEmpty()) {
                score += Math.max(missing.size() * MISSING_REQUIRED_PENALTY_EACH,
                                  MAX_MISSING_REQUIRED_PENALTY);
                reasons.add("Missing required: " + String.join(", ", missing.stream().limit(4).toList()));
            }
        }

        if (!preferred.isEmpty()) {
            double credit = 0;
            List<String> hits = new ArrayList<>();
            for (String ask : preferred) {
                double c = creditFor(ask, held, proficiencyCredit);
                credit += c;
                if (c > 0) hits.add(ask);
            }
            score += (int) Math.round((credit / preferred.size()) * PREFERRED_BUDGET);
            if (!hits.isEmpty()) {
                reasons.add("Nice-to-have you already have: "
                        + String.join(", ", hits.stream().limit(4).toList()));
            }
        }

        // Everything the posting lists but did not tier still says something about fit.
        score += scoreUntiered(job, held, reasons, TECH_OVERLAP_BUDGET);

        return Math.max(0, Math.min(score, MAX_SKILL_SCORE));
    }

    /**
     * Flat overlap between the profile and the posting's untiered skill/technology lists,
     * scaled into {@code budget}. This is the whole score for a job enriched before the
     * requirement tiers existed, and a small top-up for one enriched after.
     */
    private int scoreUntiered(Job job, Set<String> held, List<String> reasons, int budget) {
        Set<String> asks = new HashSet<>(normalizedSet(job.technologies()));
        asks.addAll(normalizedSet(job.skills()));
        if (asks.isEmpty()) return 0;

        List<String> matched = held.stream().filter(asks::contains).sorted().toList();
        if (matched.isEmpty()) return 0;

        if (budget == MAX_SKILL_SCORE) {   // untiered posting — this overlap is all we know
            reasons.add("Overlaps on: " + String.join(", ", matched.stream().limit(5).toList()));
        }
        double coverage = matched.size() / (double) asks.size();
        return (int) Math.round(Math.min(coverage, 1.0) * budget);
    }

    /**
     * 1.0 when the profile clearly holds the asked-for skill, {@value #WEAK_PROFICIENCY_CREDIT}
     * when it holds it only weakly, 0 when it does not hold it at all.
     *
     * <p>The partial tier is why we do not parse "5 års erfaring" out of the posting: the honest
     * signal is on our side of the comparison, in what the user recorded about their own depth,
     * not in a number the posting picked loosely.
     */
    private double creditFor(String ask, Set<String> held, Map<String, Double> proficiencyCredit) {
        if (ask == null || ask.isBlank()) return 0;
        String key = ask.toLowerCase().trim();
        if (!held.contains(key)) return 0;
        return proficiencyCredit.getOrDefault(key, 1.0);
    }

    /**
     * Per-skill credit keyed by normalised skill name, for skills the user recorded depth on.
     * Skills with no recorded depth are absent and default to full credit — an unrecorded
     * proficiency is missing data, not evidence of weakness.
     */
    private Map<String, Double> buildProficiencyCredit(List<ProfileSkill> profileSkills) {
        try {
            Map<String, Double> credit = new HashMap<>();
            for (ProfileSkill s : profileSkills) {
                if (s.skillName() == null) continue;
                boolean weak = "BEGINNER".equalsIgnoreCase(s.proficiencyLevel())
                        && !s.usedInProduction();
                credit.put(s.skillName().toLowerCase().trim(),
                        weak ? WEAK_PROFICIENCY_CREDIT : 1.0);
            }
            return credit;
        } catch (Exception e) {
            log.debug("Could not read skill proficiency: {}", e.getMessage());
            return Map.of();
        }
    }

    /** Kilometres from the job to the nearest preferred municipality, or MAX_VALUE if unknown. */
    private double distanceToNearestPreferred(UserPreferences prefs, Job job) {
        double[] jobCoords = DanishMunicipalityCoordinates.get(job.municipality());
        if (jobCoords == null) return Double.MAX_VALUE;
        return prefs.preferredMunicipalities().stream()
                .mapToDouble(m -> {
                    double[] c = DanishMunicipalityCoordinates.get(m);
                    return c != null
                            ? DanishMunicipalityCoordinates.haversineKm(c[0], c[1], jobCoords[0], jobCoords[1])
                            : Double.MAX_VALUE;
                }).min().orElse(Double.MAX_VALUE);
    }

    /**
     * The location bonus, tapering to zero at the edge of the accepted radius. With no radius
     * set there is no band to grade against, so anything beyond commuting distance earns
     * nothing and anything closer tapers over a nominal 50 km.
     */
    private int gradedProximityBonus(double km, Integer maxCommuteKm) {
        double band = maxCommuteKm != null ? maxCommuteKm : 50;
        if (band <= 0 || km >= band) return 0;
        return (int) Math.round(LOCATION_MATCH_BONUS * (1.0 - km / band));
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

    private static String buildProfileText(Profile p, List<ProfileSkill> skills) {
        StringBuilder sb = new StringBuilder();
        if (p.headline() != null) sb.append(p.headline()).append(' ');
        if (p.summary() != null) sb.append(p.summary()).append(' ');
        skills.stream().map(ProfileSkill::skillName).filter(Objects::nonNull)
                .forEach(name -> sb.append(name).append(' '));
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
