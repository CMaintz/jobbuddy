package com.autoapplicant.usecase.skills;

import com.autoapplicant.domain.skill.SkillMention;
import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.skill.TaxonomyCandidate;
import com.autoapplicant.port.in.skills.CurateSkillTaxonomyUseCase;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.skills.SkillTaxonomyRepositoryPort;
import com.autoapplicant.port.out.skills.TaxonomyRejectionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The taxonomy's gap list: what postings keep naming that users cannot pick.
 *
 * <p>The seeded taxonomy is a snapshot of one person's idea of the field, and the field moves. A
 * skill missing from it does not merely fail to autocomplete — it arrives uncategorised, so the CV
 * cannot group it and nothing knows whether it counts as a technology. Meanwhile enrichment has
 * been reading real labels off real postings the whole time. This turns that into a review queue.
 *
 * <p>Derived, never stored: the queue is recomputed from the postings on every request, so it
 * cannot go stale and no sweep has to keep it fresh. The only state is the admin's "no", which has
 * to stick or the same rubbish returns to the top of the list every time.
 *
 * <p>No AI is involved and none is needed. The candidates are labels enrichment already extracted;
 * asking a model to invent skills for a catalogue would produce plausible entries nobody is hiring
 * for, which is the opposite of what makes this list worth reading. The ranking is posting count,
 * for the same reason: a label 40 postings name is a gap, a label one posting names is a typo.
 */
@Service
public class SkillTaxonomyCurationService implements CurateSkillTaxonomyUseCase {

    /**
     * How many distinct labels to pull from the market before filtering. Generous: most are
     * already known, so the raw list shrinks a long way before the caller's limit applies.
     */
    private static final int MAX_MARKET_LABELS = 5000;

    /** A label this many postings name is a real gap rather than one posting's typo. */
    private static final int MIN_POSTINGS = 2;

    private final JobRepositoryPort jobRepo;
    private final SkillTaxonomyRepositoryPort taxonomyRepo;
    private final TaxonomyRejectionRepositoryPort rejectionRepo;

    public SkillTaxonomyCurationService(JobRepositoryPort jobRepo,
                                        SkillTaxonomyRepositoryPort taxonomyRepo,
                                        TaxonomyRejectionRepositoryPort rejectionRepo) {
        this.jobRepo = jobRepo;
        this.taxonomyRepo = taxonomyRepo;
        this.rejectionRepo = rejectionRepo;
    }

    @Override
    public List<TaxonomyCandidate> candidates(int limit) {
        if (limit <= 0) return List.of();
        Set<String> known = taxonomyRepo.findAllKnownNormalizedNames();
        Set<String> rejected = rejectionRepo.findRejectedNormalizedNames();

        List<TaxonomyCandidate> ranked = new ArrayList<>(mergeMarketCandidates(known, rejected));
        ranked.removeIf(candidate -> candidate.postings() < MIN_POSTINGS);
        ranked.sort(java.util.Comparator.comparingInt(TaxonomyCandidate::postings).reversed()
                .thenComparing(TaxonomyCandidate::name, String.CASE_INSENSITIVE_ORDER));
        return ranked.size() > limit ? List.copyOf(ranked.subList(0, limit)) : List.copyOf(ranked);
    }

    /**
     * Folds the market's raw skill mentions into one candidate per normalized name, skipping labels
     * the taxonomy already knows or an admin has rejected. Two spellings of one skill combine via
     * {@link #combine}.
     *
     * <p>The database folds case and space; this folds the rest, so "Node.js" and "node.js " are
     * one candidate keyed exactly as every other skill lookup keys it.
     */
    private Collection<TaxonomyCandidate> mergeMarketCandidates(Set<String> known, Set<String> rejected) {
        Map<String, TaxonomyCandidate> merged = new LinkedHashMap<>();
        for (SkillMention mention : jobRepo.findSkillMentions(MAX_MARKET_LABELS)) {
            String normalized = SkillNames.normalize(mention.label());
            if (normalized.isEmpty() || known.contains(normalized) || rejected.contains(normalized)) continue;
            merged.merge(normalized,
                    new TaxonomyCandidate(mention.label().strip(), normalized, mention.postings(),
                            mention.readAsTechnology()),
                    SkillTaxonomyCurationService::combine);
        }
        return merged.values();
    }

    /**
     * Two spellings of one skill: counts add up, and the more-named spelling supplies the name.
     * Rarely reached — the database already folded case and space — so a posting naming both
     * spellings being counted twice is an over-count nobody will see, and it errs toward
     * surfacing a candidate rather than hiding one.
     */
    private static TaxonomyCandidate combine(TaxonomyCandidate a, TaxonomyCandidate b) {
        TaxonomyCandidate leading = a.postings() >= b.postings() ? a : b;
        return new TaxonomyCandidate(leading.name(), a.normalizedName(),
                a.postings() + b.postings(), leading.readAsTechnology());
    }

    @Override
    public SkillTaxonomy approve(String name, String category) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        if (category == null || category.isBlank()) {
            // Deliberately not defaulted. A row's category decides how the CV groups the skill and
            // whether it counts as a technology; guessing it here would put a wrong answer into
            // reference data every user then inherits.
            throw new IllegalArgumentException("Category must be chosen when approving a skill");
        }
        String trimmed = name.strip();
        String normalized = SkillNames.normalize(trimmed);
        // Written against the repository, not SkillTaxonomyService.createOrGet: use cases do not
        // call each other. The semantics differ anyway — an approval carries a chosen category,
        // where createOrGet falls back to "Custom".
        return taxonomyRepo.findByNormalizedName(normalized)
                .orElseGet(() -> taxonomyRepo.save(
                        new SkillTaxonomy(null, trimmed, normalized, null, category.strip(), List.of())));
    }

    @Override
    public void reject(String name, String reason) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name must not be blank");
        }
        String trimmed = name.strip();
        rejectionRepo.reject(SkillNames.normalize(trimmed), trimmed, reason);
    }
}
