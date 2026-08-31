package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.EvidenceGap;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.InterviewStory;
import com.autoapplicant.port.in.skills.GetEvidenceGapsUseCase;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Finds the skills worth asking for evidence about, and stores the answers.
 *
 * <p>A skill qualifies when all three hold: the candidate claims it, the postings they match ask
 * for it, and no story in the bank mentions it. The first condition keeps this honest (we never
 * ask for evidence of something they did not claim), the second makes it worth their time, and the
 * third is the actual gap.
 *
 * <p>Deterministic — no AI. The question is a template, which is enough to get a real answer out of
 * someone; the model's turn comes later, when there are enough answers to make batching worthwhile.
 */
@Service
public class EvidenceGapService implements GetEvidenceGapsUseCase {

    private final ProfileSkillRepositoryPort profileSkillRepo;
    private final InterviewStoryRepositoryPort storyRepo;
    private final MarketCorpusService marketCorpus;

    public EvidenceGapService(ProfileSkillRepositoryPort profileSkillRepo,
                              InterviewStoryRepositoryPort storyRepo,
                              MarketCorpusService marketCorpus) {
        this.profileSkillRepo = profileSkillRepo;
        this.storyRepo = storyRepo;
        this.marketCorpus = marketCorpus;
    }

    @Override
    public List<EvidenceGap> evidenceGaps(UUID userId, int limit) {
        Map<String, String> claimed = claimedSkills(userId);
        if (claimed.isEmpty()) return List.of();

        Evidence evidence = existingEvidence(userId);
        Map<String, Integer> demand = marketDemand(userId);

        List<EvidenceGap> gaps = new ArrayList<>();
        claimed.forEach((normalized, displayName) -> {
            if (evidence.proves(normalized)) return;
            int frequency = demand.getOrDefault(normalized, 0);
            if (frequency == 0) return;   // nobody is asking; the gap costs nothing today
            gaps.add(new EvidenceGap(displayName, frequency, question(displayName)));
        });

        return gaps.stream()
                .sorted(Comparator.comparingInt(EvidenceGap::marketFrequency).reversed()
                        .thenComparing(EvidenceGap::skillName))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public InterviewStory recordEvidence(UUID userId, String skillName, String situation,
                                         String action, String result) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("Evidence needs the skill it is evidence for");
        }
        if ((action == null || action.isBlank()) && (result == null || result.isBlank())) {
            // Situation alone is context, not evidence — it proves nothing a letter could cite.
            throw new IllegalArgumentException("Evidence needs what you did or what came of it");
        }
        String skill = skillName.strip();
        return storyRepo.save(new InterviewStory(null, userId, skill, blankToNull(situation), null,
                blankToNull(action), blankToNull(result), null, List.of(skill), null, null));
    }

    /**
     * What the story bank already proves.
     *
     * @param taggedSkills normalized tags — the reliable signal, written by this feature
     * @param storyBodies  the stories' text, searched as a courtesy for stories written by hand
     *                     before tagging existed
     */
    private record Evidence(Set<String> taggedSkills, List<String> storyBodies) {
        boolean proves(String normalizedSkill) {
            return taggedSkills.contains(normalizedSkill)
                    || storyBodies.stream().anyMatch(body -> body.contains(normalizedSkill));
        }
    }

    private Evidence existingEvidence(UUID userId) {
        Set<String> tags = new LinkedHashSet<>();
        List<String> bodies = new ArrayList<>();
        for (InterviewStory story : storyRepo.findByUserId(userId)) {
            if (story.tags() != null) {
                story.tags().stream().filter(Objects::nonNull).forEach(tag -> tags.add(normalize(tag)));
            }
            String body = String.join(" ",
                    orEmpty(story.title()), orEmpty(story.situation()), orEmpty(story.task()),
                    orEmpty(story.action()), orEmpty(story.result())).toLowerCase(Locale.ROOT).strip();
            if (!body.isBlank()) bodies.add(body);
        }
        return new Evidence(tags, bodies);
    }

    private Map<String, String> claimedSkills(UUID userId) {
        Map<String, String> claimed = new LinkedHashMap<>();
        profileSkillRepo.findByUserId(userId).stream()
                .map(ProfileSkill::skillName)
                .filter(name -> name != null && !name.isBlank())
                .forEach(name -> claimed.put(normalize(name), name.strip()));
        return claimed;
    }

    private static void addAll(Map<String, String> target, List<String> names) {
        if (names == null) return;
        names.stream().filter(n -> n != null && !n.isBlank())
                .forEach(n -> target.putIfAbsent(normalize(n), n.strip()));
    }

    private Map<String, Integer> marketDemand(UUID userId) {
        Map<String, Integer> demand = new LinkedHashMap<>();
        for (Job job : marketCorpus.collect(userId, false)) {
            Set<String> seen = new LinkedHashSet<>();
            if (job.technologies() != null) job.technologies().forEach(t -> add(seen, t));
            if (job.skills() != null) job.skills().forEach(t -> add(seen, t));
            seen.forEach(name -> demand.merge(name, 1, Integer::sum));
        }
        return demand;
    }

    private static void add(Set<String> target, String value) {
        if (value != null && !value.isBlank()) target.add(normalize(value));
    }

    /**
     * The opening question. Deliberately asks for a specific incident rather than a description —
     * "where and what changed" is answerable in two sentences and produces something citable,
     * where "describe your experience with X" produces another paragraph of adjectives.
     */
    static String question(String skill) {
        return "Where did you use " + skill + ", and what was different afterwards?";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }

    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.strip() : null;
    }
}
