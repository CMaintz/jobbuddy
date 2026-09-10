package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.skill.SkillCandidate;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.skill.SkillConfirmation;
import com.autoapplicant.port.in.skills.SuggestSkillCandidatesUseCase;
import com.autoapplicant.port.out.skills.ProfileSkillRepositoryPort;
import com.autoapplicant.port.out.skills.ParsedSkillSuggestionRepositoryPort;
import com.autoapplicant.port.out.skills.SkillCandidateDismissalRepositoryPort;
import com.autoapplicant.domain.skill.ParsedSkillSuggestion;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.usecase.job.MarketCorpusService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Suggests skills the user has not claimed — passes 1 and 2 of the elicitation design, with no AI
 * involved at all.
 *
 * <p>Two sources, deliberately combined:
 *
 * <ul>
 *   <li><b>Taxonomy adjacency</b> — the children and siblings of skills the user already has. This
 *       is the "big list" idea, scoped to what sits next to their real skills rather than to an
 *       industry catalogue, which keeps it short and plausible.</li>
 *   <li><b>Market demand</b> — skills named by the postings this user actually matches, whether or
 *       not the taxonomy knows them. A posting is evidence that the skill is worth listing; a
 *       catalogue is not.</li>
 * </ul>
 *
 * <p>Ranking is by market frequency first, because the question worth a user's attention is not
 * "which skills exist in your field" but "which of your unlisted skills would change your next ten
 * applications". A candidate nobody is hiring for is a candidate not worth asking about.
 *
 * <p>The service never invents a skill: every candidate comes from the seeded taxonomy or from a
 * real posting, and nothing reaches the profile without the user saying yes.
 */
@Service
public class SkillCandidateService implements SuggestSkillCandidatesUseCase {

    /** Candidates with no market signal at all are only worth offering when there is room. */
    private static final int MAX_UNDEMANDED_CANDIDATES = 5;

    private final ProfileSkillRepositoryPort profileSkillRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;
    private final SkillCandidateDismissalRepositoryPort dismissalRepo;
    private final MarketCorpusService marketCorpus;
    private final ParsedSkillSuggestionRepositoryPort parsedSuggestionRepo;
    private final SkillResolver resolver;

    public SkillCandidateService(ProfileSkillRepositoryPort profileSkillRepo,
                                 SkillTaxonomyRepositoryPort taxonomyRepo,
                                 SkillCandidateDismissalRepositoryPort dismissalRepo,
                                 MarketCorpusService marketCorpus,
                                 ParsedSkillSuggestionRepositoryPort parsedSuggestionRepo,
                                 SkillResolver resolver) {
        this.profileSkillRepo = profileSkillRepo;
        this.taxonomyRepo = taxonomyRepo;
        this.dismissalRepo = dismissalRepo;
        this.marketCorpus = marketCorpus;
        this.parsedSuggestionRepo = parsedSuggestionRepo;
        this.resolver = resolver;
    }

    @Override
    public List<SkillCandidate> suggest(UUID userId, int limit) {
        Map<String, String> claimed = claimedSkills(userId);
        Set<String> dismissed = dismissalRepo.findDismissedNames(userId);

        // Market first: it decides both which candidates exist and how they rank.
        Map<String, MarketMention> market = marketMentions(userId);
        Adjacency adjacent = taxonomyAdjacency(claimed);
        Map<String, List<String>> adjacency = adjacent.relatedSkills();

        Map<String, SkillCandidate> candidates = new LinkedHashMap<>();

        // Document inferences go in first, and win any collision: a skill the user's own CV
        // evidences is a better-founded question than the same skill inferred from a neighbour or
        // from what strangers are hiring for, and its quoted line is the reason the user can judge.
        for (ParsedSkillSuggestion suggestion : parsedSuggestionRepo.findByUserId(userId)) {
            String normalized = suggestion.normalizedName();
            if (normalized == null || claimed.containsKey(normalized) || dismissed.contains(normalized)) continue;
            candidates.put(normalized, new SkillCandidate(
                    suggestion.skillName(), null,
                    market.containsKey(normalized) ? market.get(normalized).count() : 0,
                    adjacency.getOrDefault(normalized, List.of()),
                    SkillCandidate.SkillCandidateSource.DOCUMENT_INFERRED,
                    suggestion.evidence()));
        }

        market.forEach((normalized, mention) -> {
            if (claimed.containsKey(normalized) || dismissed.contains(normalized)
                    || candidates.containsKey(normalized)) return;
            List<String> related = adjacency.getOrDefault(normalized, List.of());
            candidates.put(normalized, new SkillCandidate(mention.displayName(), null,
                    mention.count(), related,
                    related.isEmpty() ? SkillCandidate.SkillCandidateSource.MARKET_DEMAND
                            : SkillCandidate.SkillCandidateSource.BOTH));
        });
        adjacency.forEach((normalized, related) -> {
            if (claimed.containsKey(normalized) || dismissed.contains(normalized)
                    || candidates.containsKey(normalized)) return;
            candidates.put(normalized, new SkillCandidate(
                    adjacent.displayNames().getOrDefault(normalized, normalized), null, 0, related,
                    SkillCandidate.SkillCandidateSource.TAXONOMY_ADJACENT));
        });

        List<SkillCandidate> ranked = candidates.values().stream()
                .sorted(Comparator
                        // A skill the user's own document evidences ranks above one merely in
                        // demand: it is the one suggestion they can answer from memory.
                        .comparing((SkillCandidate c) ->
                                c.source() == SkillCandidate.SkillCandidateSource.DOCUMENT_INFERRED ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(SkillCandidate::marketFrequency).reversed())
                        .thenComparing(c -> -c.relatedSkills().size())
                        .thenComparing(SkillCandidate::name))
                .toList();

        // Keep the tail of no-demand suggestions short: past a handful they are noise, and the
        // screen's credibility rests on every row having a reason.
        List<SkillCandidate> result = new ArrayList<>();
        int undemanded = 0;
        for (SkillCandidate candidate : ranked) {
            if (result.size() >= Math.max(1, limit)) break;
            // The no-demand cap exists to stop a tail of unexplained rows; a document inference
            // always carries its own explanation, so it is never part of that tail.
            if (candidate.source() != SkillCandidate.SkillCandidateSource.DOCUMENT_INFERRED
                    && candidate.marketFrequency() == 0
                    && ++undemanded > MAX_UNDEMANDED_CANDIDATES) continue;
            result.add(candidate);
        }
        return result;
    }

    @Override
    public List<ProfileSkill> confirm(UUID userId, List<SkillConfirmation> confirmations) {
        if (confirmations == null || confirmations.isEmpty()) return List.of();
        List<ProfileSkill> owned = profileSkillRepo.findByUserId(userId);
        // Dedup on the canonical key so confirming "k8s" is a no-op when Kubernetes is already held.
        Set<String> heldKeys = new HashSet<>();
        owned.forEach(s -> heldKeys.add(resolver.key(s.skillName())));
        int displayOrder = owned.size();

        List<ProfileSkill> added = new ArrayList<>();
        for (SkillConfirmation confirmation : confirmations) {
            String name = confirmation.name();
            if (name == null || name.isBlank() || confirmation.decision() == null) continue;
            // Dismissals and the suggestion queue are keyed by the candidate's own spelling; the
            // canonical key is only for recognising what the profile already holds.
            String normalized = normalize(name);
            String key = resolver.key(name);

            switch (confirmation.decision()) {
                case NO -> {
                    dismissalRepo.dismiss(userId, normalized);
                    parsedSuggestionRepo.remove(userId, normalized);
                }
                case YES -> {
                    // Confirming something already on the profile is a no-op, not a duplicate row —
                    // but the question is answered either way, so it leaves the queue.
                    if (heldKeys.contains(key)) {
                        parsedSuggestionRepo.remove(userId, normalized);
                        continue;
                    }
                    // Collapse onto the taxonomy master so a confirmed "k8s" is stored as Kubernetes.
                    SkillTaxonomy master = resolver.master(name).orElse(null);
                    ProfileSkill saved = profileSkillRepo.save(new ProfileSkill(
                            null, userId,
                            master != null ? master.name() : name.strip(),
                            master != null ? master.id() : null,
                            null,
                            confirmation.yearsExperience(), confirmation.usedInProduction(),
                            displayOrder++, master != null ? master.category() : null));
                    heldKeys.add(key);
                    parsedSuggestionRepo.remove(userId, normalized);
                    added.add(saved);
                }
                case SKIP -> { /* comes back next time, by design */ }
            }
        }
        return added;
    }

    /** Normalized name → display name for everything already on the profile. */
    private Map<String, String> claimedSkills(UUID userId) {
        Map<String, String> claimed = new HashMap<>();
        profileSkillRepo.findByUserId(userId).stream()
                .map(ProfileSkill::skillName)
                .filter(Objects::nonNull)
                .forEach(name -> claimed.put(normalize(name), name));
        return claimed;
    }

    private static void addAll(Map<String, String> target, List<String> names) {
        if (names == null) return;
        names.stream().filter(n -> n != null && !n.isBlank())
                .forEach(n -> target.put(normalize(n), n.strip()));
    }

    /** How often each skill appears across the postings this user matches. */
    private Map<String, MarketMention> marketMentions(UUID userId) {
        Map<String, MarketMention> mentions = new LinkedHashMap<>();
        for (Job job : marketCorpus.collect(userId, false)) {
            // A posting that names the same skill in both lists still only counts once.
            Set<String> seen = new LinkedHashSet<>();
            addMentions(seen, job.technologies());
            addMentions(seen, job.skills());
            for (String name : seen) {
                MarketMention existing = mentions.get(normalize(name));
                mentions.put(normalize(name), existing == null
                        ? new MarketMention(name, 1)
                        : new MarketMention(existing.displayName(), existing.count() + 1));
            }
        }
        return mentions;
    }

    private static void addMentions(Set<String> target, List<String> names) {
        if (names == null) return;
        names.stream().filter(n -> n != null && !n.isBlank()).map(String::strip).forEach(target::add);
    }

    /**
     * The adjacency walk's two outputs, returned together rather than accumulated in a field: this
     * is a singleton service, so per-request state on the instance would leak between users.
     *
     * @param relatedSkills candidate normalized name → the user's own skills it sits next to
     * @param displayNames  candidate normalized name → the taxonomy's spelling of it
     */
    private record Adjacency(Map<String, List<String>> relatedSkills, Map<String, String> displayNames) {}

    private Adjacency taxonomyAdjacency(Map<String, String> claimed) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Map<String, String> displayNames = new HashMap<>();
        for (Map.Entry<String, String> entry : claimed.entrySet()) {
            var node = taxonomyRepo.findByNormalizedName(entry.getKey()).orElse(null);
            if (node == null) continue;

            List<UUID> parents = new ArrayList<>();
            parents.add(node.id());                                   // children of this skill
            if (node.parentId() != null) parents.add(node.parentId()); // and its siblings
            for (var neighbour : taxonomyRepo.findByParentIds(parents)) {
                String normalized = neighbour.normalizedName() != null
                        ? neighbour.normalizedName() : normalize(neighbour.name());
                if (normalized.equals(entry.getKey())) continue;
                adjacency.computeIfAbsent(normalized, k -> new ArrayList<>()).add(entry.getValue());
                displayNames.putIfAbsent(normalized, neighbour.name());
            }
        }
        return new Adjacency(adjacency, displayNames);
    }

    static String normalize(String name) {
        return SkillNames.normalize(name);
    }

    /** A skill seen in the market, with the spelling the postings used. */
    private record MarketMention(String displayName, int count) {}
}
