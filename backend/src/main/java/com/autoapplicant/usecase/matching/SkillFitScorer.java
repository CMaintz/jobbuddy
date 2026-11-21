package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.usecase.common.KeywordMatcher;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
 * {@link #buildProficiencyCredit}); a related one ({@link CompoundHeads}) or one the profile
 * names in its own headline or summary counts half, but all of that loose evidence together is
 * capped at {@value #MAX_RELATED_EVIDENCE}, so it can lift a fit the profile's own skills already
 * show and never make one on its own. A demanded skill weighs more than a welcome one, and both
 * more than an ask the posting did not tier. Missing demands still cost, scaled by the share
 * missing and capped, so a long list of labels nobody holds cannot zero a good fit.
 */
final class SkillFitScorer {

    private static final int EVIDENCE_SCORE = 55;
    private static final int MAX_COVERAGE_BONUS = 10;
    static final int MAX_SCORE = EVIDENCE_SCORE + MAX_COVERAGE_BONUS;
    /** Share of required asks answered below which no bonus is earned. */
    private static final double BONUS_FROM_COVERAGE = 0.5;
    private static final double FULL_EVIDENCE = 2.5;
    private static final double MAX_RELATED_EVIDENCE = 1.0;
    private static final double REQUIRED_WEIGHT = 1.0;
    private static final double PREFERRED_WEIGHT = 0.75;
    private static final double UNTIERED_WEIGHT = 0.5;
    private static final double RELATED_CREDIT = 0.5;
    private static final int MAX_MISSING_REQUIRED_PENALTY = 8;
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
    private record Credit(double exact, double related) {
        static final Credit NONE = new Credit(0, 0);

        boolean isNone() {
            return exact == 0 && related == 0;
        }
    }

    private final SkillCanonicalizer canonicalizer;
    private final List<String> heldLabels;
    private final List<String> heldHeads;
    /** Canonical skill key to credit; a held skill with no recorded depth gets full credit. */
    private final Map<String, Double> held;
    private final String ownWords;

    private SkillFitScorer(SkillCanonicalizer canonicalizer, List<String> heldLabels,
                           Map<String, Double> held, String ownWords) {
        this.canonicalizer = canonicalizer;
        this.heldLabels = heldLabels;
        this.heldHeads = heldLabels.stream().map(CompoundHeads::headOf).distinct().toList();
        this.held = held;
        this.ownWords = ownWords;
    }

    static SkillFitScorer forProfile(SkillCanonicalizer canonicalizer, Profile profile,
                                     List<ProfileSkill> skills) {
        List<String> labels = skills.stream().map(ProfileSkill::skillName)
                .filter(Objects::nonNull).filter(s -> !s.isBlank()).toList();
        String ownWords = normalized(Objects.toString(profile.headline(), "") + " "
                + Objects.toString(profile.summary(), ""));
        return new SkillFitScorer(canonicalizer, labels,
                buildProficiencyCredit(canonicalizer, skills), ownWords);
    }

    /** The skill fit for {@code job}, appending what it found to {@code reasons}. */
    int score(Job job, List<String> reasons) {
        PostingAsks asks = PostingAsks.of(job, canonicalizer);
        if (asks.isEmpty()) {
            asks = PostingAsks.untiered(namedInText(job));
        }
        Map<String, Credit> credits = new HashMap<>();
        asks.all().forEach(ask -> credits.computeIfAbsent(ask, this::creditFor));
        double evidence = weighted(asks, credits, Credit::exact)
                + Math.min(weighted(asks, credits, Credit::related), MAX_RELATED_EVIDENCE);
        double strength = Math.min(evidence / FULL_EVIDENCE, 1.0);
        int score = (int) Math.round(strength * EVIDENCE_SCORE
                        + coverageBonus(strength, asks.required(), credits))
                - (int) Math.round(MAX_MISSING_REQUIRED_PENALTY
                        * missingShare(asks.required(), credits));
        explain(asks, credits, reasons);
        return Math.max(0, Math.min(score, MAX_SCORE));
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
        return isRelated(ask) || isInOwnWords(ask) ? new Credit(0, RELATED_CREDIT) : Credit.NONE;
    }

    private boolean isRelated(String ask) {
        String head = CompoundHeads.headOf(ask);
        return heldHeads.stream().anyMatch(heldHead -> CompoundHeads.related(head, heldHead));
    }

    private boolean isInOwnWords(String ask) {
        return canonicalizer.surfaceForms(ask).stream()
                .anyMatch(form -> containsPhrase(ownWords, normalized(form)));
    }

    /**
     * Whole-phrase containment by plain search. The profile's own words are checked against every
     * distinct ask of every posting, and compiling a pattern per ask would churn the shared
     * {@link KeywordMatcher} cache for every other caller.
     */
    private static boolean containsPhrase(String text, String phrase) {
        if (phrase.isEmpty()) return false;
        for (int at = text.indexOf(phrase); at >= 0; at = text.indexOf(phrase, at + 1)) {
            int end = at + phrase.length();
            boolean startOk = at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
            boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
        }
        return false;
    }

    private static String normalized(String text) {
        return WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /** Share of required asks with no evidence at all; a posting that tiers nothing misses none. */
    private static double missingShare(List<String> required, Map<String, Credit> credits) {
        if (required.isEmpty()) return 0;
        long missing = required.stream().filter(ask -> credits.get(ask).isNone()).count();
        return missing / (double) required.size();
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
     * anyone. The profile's own skills that its text names stand in for them. Only the label as
     * the user wrote it is searched, not taxonomy aliases: a one-word alias such as "presse"
     * turns up in the page chrome of every crawled posting. Share buttons are dropped for the
     * same reason.
     */
    private List<String> namedInText(Job job) {
        String text = Arrays.stream((Objects.toString(job.title(), "") + "\n"
                        + Objects.toString(job.descriptionClean(), "")).split("\n"))
                .filter(line -> !isShareButton(line))
                .collect(Collectors.joining("\n"));
        return heldLabels.stream().filter(label -> KeywordMatcher.contains(text, label)).toList();
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
