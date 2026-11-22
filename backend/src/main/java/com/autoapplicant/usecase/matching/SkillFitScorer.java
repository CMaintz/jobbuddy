package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.usecase.common.KeywordMatcher;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * How well one profile's skills answer a posting's asks, on a 0-{@value #MAX_SCORE} scale.
 *
 * <p>The score counts evidence rather than coverage. Enrichment lists every ask a posting makes,
 * often ten or twenty labels per posting, many of them ("Clean code", "Målgruppeforståelse") no
 * profile holds as a skill. Scoring the share of that list a profile covers, with a penalty per
 * miss, puts every real posting near zero for every profile: a developer holding four of nine
 * asks lands where a nurse posting does. A handful of real matches is what tells a good fit from
 * a bad one, so {@value #FULL_EVIDENCE} points of evidence earn the {@value #EVIDENCE_SCORE}
 * points that evidence can give. The constants are held to real postings by
 * {@code MatchScoreEvalTest}.
 *
 * <p>Evidence alone tops out where a fit and a near miss look alike: a technical security post
 * hands a GRC profile as much evidence as a GRC post does. What sets the best fits apart is that
 * they answer most of what the posting demands, so answering more than half of the required asks
 * earns up to {@value #MAX_COVERAGE_BONUS} more, scaled by the evidence, and that is the only way
 * into the top band.
 *
 * <p>Evidence per ask: a skill the profile holds counts in full (half for a weak one, see
 * {@link #buildProficiencyCredit}). One it very likely answers without holding it, by spelling,
 * by meaning or by a suggestion the user has not confirmed yet, earns partial credit
 * ({@link LooseCredit}), but all of that loose evidence together is capped, so it can lift a fit
 * the profile's own skills already show and never make one on its own. The cap is
 * {@value #BASE_RELATED_EVIDENCE}, and each point of held evidence widens it by
 * {@value #RELATED_PER_HELD} up to {@value #MAX_RELATED_EVIDENCE}: a posting the profile shares
 * nothing with gets only the base, while one its own skills already answer can also show the
 * adjacent asks it covers, which a flat cap spends on its spelling matches alone. A demanded
 * skill weighs more than a welcome one, and both more than an ask the posting did not tier.
 * Missing demands still cost, scaled by the share missing and capped, so a long list of labels
 * nobody holds cannot zero a good fit.
 */
final class SkillFitScorer {

    private static final int EVIDENCE_SCORE = 55;
    private static final int MAX_COVERAGE_BONUS = 10;
    static final int MAX_SCORE = EVIDENCE_SCORE + MAX_COVERAGE_BONUS;
    /** Share of required asks answered below which no bonus is earned. */
    private static final double BONUS_FROM_COVERAGE = 0.5;
    private static final double FULL_EVIDENCE = 2.5;
    private static final double BASE_RELATED_EVIDENCE = 1.0;
    private static final double RELATED_PER_HELD = 0.5;
    private static final double MAX_RELATED_EVIDENCE = 1.5;
    private static final double REQUIRED_WEIGHT = 1.0;
    private static final double PREFERRED_WEIGHT = 0.75;
    private static final double UNTIERED_WEIGHT = 0.5;
    private static final int MAX_MISSING_REQUIRED_PENALTY = 8;
    /** Credit at which an ask no longer counts as missing: a related skill's. */
    private static final double ANSWERED = LooseCredit.RELATED_CREDIT;
    private static final int REASON_LIMIT = 4;

    // A skill listed at BEGINNER and never used in production is not the same evidence as one
    // used in production for years.
    private static final double WEAK_PROFICIENCY_CREDIT = 0.5;

    /** A share or follow button ("Del på sociale medier") is page chrome, not an ask. */
    private static final Pattern SHARE_BUTTON =
            Pattern.compile("^\\s*(del|følg os|share|follow us)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int MAX_SHARE_BUTTON_WORDS = 6;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** What one ask earns: full credit for a held skill, or loose credit for a related one. */
    private record Credit(double exact, double related) {}

    private final SkillCanonicalizer canonicalizer;
    /** Labels a posting's text is searched for when it was never enriched. */
    private final List<String> searchLabels;
    /** Canonical skill key to credit; a held skill with no recorded depth gets full credit. */
    private final Map<String, Double> held;
    private final LooseCredit loose;

    private SkillFitScorer(SkillCanonicalizer canonicalizer, List<String> searchLabels,
                           Map<String, Double> held, LooseCredit loose) {
        this.canonicalizer = canonicalizer;
        this.searchLabels = searchLabels;
        this.held = held;
        this.loose = loose;
    }

    /**
     * @param suggested  skills proposed to the user and not yet confirmed; loose credit only
     * @param similarity how close two labels are in meaning, {@link SkillSimilarity#NONE} if
     *                   that is unknown
     */
    static SkillFitScorer forProfile(SkillCanonicalizer canonicalizer, Profile profile,
                                     List<ProfileSkill> skills, List<String> suggested,
                                     SkillSimilarity similarity) {
        MatchSkills labels = new MatchSkills(skills.stream().map(ProfileSkill::skillName)
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).toList(), suggested);
        String ownWords = Objects.toString(profile.headline(), "") + " "
                + Objects.toString(profile.summary(), "");
        List<String> searchLabels = new ArrayList<>(labels.held());
        searchLabels.addAll(labels.suggested());
        return new SkillFitScorer(canonicalizer, List.copyOf(searchLabels),
                buildProficiencyCredit(canonicalizer, skills),
                new LooseCredit(canonicalizer, labels, ownWords, similarity));
    }

    /** The skill fit for {@code job}, appending what it found to {@code reasons}. */
    int score(Job job, List<String> reasons) {
        PostingAsks asks = PostingAsks.of(job, canonicalizer);
        if (asks.isEmpty()) {
            asks = PostingAsks.untiered(namedInText(job));
        }
        Map<String, Credit> credits = new HashMap<>();
        asks.all().forEach(ask -> credits.computeIfAbsent(ask, this::creditFor));
        double strength = Math.min(evidence(asks, credits) / FULL_EVIDENCE, 1.0);
        int score = (int) Math.round(strength * EVIDENCE_SCORE
                        + coverageBonus(strength, asks.required(), credits))
                - (int) Math.round(MAX_MISSING_REQUIRED_PENALTY
                        * missingShare(asks.required(), credits));
        explain(asks, credits, reasons);
        return Math.max(0, Math.min(score, MAX_SCORE));
    }

    /** Held evidence plus loose evidence, the loose part capped by how much is held. */
    private static double evidence(PostingAsks asks, Map<String, Credit> credits) {
        double heldEvidence = weighted(asks, credits, Credit::exact);
        double looseCap = Math.min(BASE_RELATED_EVIDENCE + RELATED_PER_HELD * heldEvidence,
                MAX_RELATED_EVIDENCE);
        return heldEvidence + Math.min(weighted(asks, credits, Credit::related), looseCap);
    }

    private static double weighted(PostingAsks asks, Map<String, Credit> credits,
                                   ToDoubleFunction<Credit> part) {
        return REQUIRED_WEIGHT * sum(asks.required(), credits, part)
                + PREFERRED_WEIGHT * sum(asks.preferred(), credits, part)
                + UNTIERED_WEIGHT * sum(asks.untiered(), credits, part);
    }

    private static double sum(List<String> asks, Map<String, Credit> credits,
                              ToDoubleFunction<Credit> part) {
        return asks.stream().map(credits::get).mapToDouble(part).sum();
    }

    private Credit creditFor(String ask) {
        Double exact = held.get(canonicalizer.canonical(ask));
        if (exact != null) return new Credit(exact, 0);
        return new Credit(0, loose.of(ask));
    }

    /**
     * Share of required asks left unanswered; a posting that tiers nothing misses none. A held or
     * related skill answers an ask fully, weaker loose credit only in part: a faint likeness to a
     * suggested skill must not wipe a whole miss off a posting the profile does not fit.
     */
    private static double missingShare(List<String> required, Map<String, Credit> credits) {
        if (required.isEmpty()) return 0;
        double missing = required.stream().map(credits::get)
                .mapToDouble(c -> 1 - Math.min(1, Math.max(c.exact(), c.related()) / ANSWERED))
                .sum();
        return missing / required.size();
    }

    /**
     * Nothing until more than half of the required asks are answered, the whole bonus when all
     * are. A posting that tiers nothing earns none: there is no list of demands to answer.
     *
     * <p>An ask is answered by its credit, so a related skill answers half of one. Counting it in
     * full put a technical security post, read by a GRC profile, on the edge of the top band.
     */
    private static double coverageBonus(double strength, List<String> required,
                                        Map<String, Credit> credits) {
        if (required.isEmpty()) return 0;
        double answered = required.stream().map(credits::get)
                .mapToDouble(c -> Math.max(c.exact(), c.related())).sum() / required.size();
        if (answered <= BONUS_FROM_COVERAGE) return 0;
        return MAX_COVERAGE_BONUS * strength
                * (answered - BONUS_FROM_COVERAGE) / (1 - BONUS_FROM_COVERAGE);
    }

    /**
     * A posting that was never enriched has no ask lists at all, so it would score nothing for
     * anyone. The profile's own skills that its text names stand in for them, suggested ones
     * included at their loose credit. Only the label as
     * the user wrote it is searched, not taxonomy aliases: a one-word alias such as "presse"
     * turns up in the page chrome of every crawled posting. Share buttons are dropped for the
     * same reason.
     */
    private List<String> namedInText(Job job) {
        String text = Arrays.stream((Objects.toString(job.title(), "") + "\n"
                        + Objects.toString(job.descriptionClean(), "")).split("\n"))
                .filter(line -> !isShareButton(line))
                .collect(Collectors.joining("\n"));
        return searchLabels.stream().filter(label -> KeywordMatcher.contains(text, label)).toList();
    }

    private static boolean isShareButton(String line) {
        return SHARE_BUTTON.matcher(line).find()
                && WHITESPACE.split(line.strip()).length <= MAX_SHARE_BUTTON_WORDS;
    }

    /** Only a held skill counts as covered here, so a loose match never reads as a met demand. */
    private static void explain(PostingAsks asks, Map<String, Credit> credits,
                                List<String> reasons) {
        explainRequired(asks.required(), credits, reasons);
        addListed(reasons, "Nice-to-have you already have: ", heldOf(asks.preferred(), credits));
        if (asks.required().isEmpty() && asks.preferred().isEmpty()) {
            addListed(reasons, "Overlaps on: ", heldOf(asks.untiered(), credits));
        }
    }

    private static void explainRequired(List<String> required, Map<String, Credit> credits,
                                        List<String> reasons) {
        if (required.isEmpty()) return;
        List<String> missing = required.stream()
                .filter(ask -> credits.get(ask).exact() == 0).toList();
        reasons.add((required.size() - missing.size()) + "/" + required.size()
                + " required skill" + (required.size() > 1 ? "s" : "") + " covered");
        addListed(reasons, "Missing required: ", missing);
    }

    private static List<String> heldOf(List<String> asks, Map<String, Credit> credits) {
        return asks.stream().filter(ask -> credits.get(ask).exact() > 0).toList();
    }

    private static void addListed(List<String> reasons, String prefix, List<String> labels) {
        if (!labels.isEmpty()) {
            reasons.add(prefix + String.join(", ", labels.stream().limit(REASON_LIMIT).toList()));
        }
    }

    /**
     * Per-skill credit keyed by canonical skill name. A skill with no recorded depth gets full
     * credit: an unrecorded proficiency is missing data, not evidence of weakness.
     */
    private static Map<String, Double> buildProficiencyCredit(SkillCanonicalizer canonicalizer,
                                                              List<ProfileSkill> skills) {
        Map<String, Double> credit = new HashMap<>();
        for (ProfileSkill s : skills) {
            String key = s.skillName() == null ? "" : canonicalizer.canonical(s.skillName());
            if (key.isEmpty()) continue;
            boolean weak = "BEGINNER".equalsIgnoreCase(s.proficiencyLevel()) && !s.usedInProduction();
            credit.merge(key, weak ? WEAK_PROFICIENCY_CREDIT : 1.0, Math::max);
        }
        return credit;
    }
}
