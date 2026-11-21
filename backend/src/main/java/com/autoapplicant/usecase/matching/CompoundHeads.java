package com.autoapplicant.usecase.matching;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Whether two skill labels name related skills although their spellings differ, by comparing
 * the word each label ends on.
 *
 * <p>Danish writes a skill as one compound or as a phrase, and the same field turns up under
 * many of them: a posting asks for "Kommunikation" or "Kommunikationsstrategi" from someone whose
 * profile says "Intern kommunikation"; it asks for "Koordinering" from someone who wrote
 * "Projektkoordinering". Exact matching scores all of those as unrelated, which is why a
 * communications profile came out as a weak match for every communications posting. A head word
 * that contains the other one, at its start or its end, is treated as related.
 *
 * <p>This is not stemming, and it is only safe because related labels earn capped, partial
 * credit in {@link SkillFitScorer}, never a full match. Three rules keep it narrow:
 * <ul>
 *   <li>Only the last word is compared, so "Intern audit" and "Intern kommunikation" stay apart.
 *   <li>A hyphen does not split a word, so "IT-sikkerhed" does not end on "sikkerhed" and say
 *       something about "Sikkerhedsgodkendelse".
 *   <li>A shared head shorter than {@value #MIN_HEAD} letters, or one of {@link #GENERIC_HEADS},
 *       relates nothing: "SQL" is not answered by "PostgreSQL", and "Risk management" is not
 *       "Project management".
 * </ul>
 */
final class CompoundHeads {

    /** Shorter than this, a head is too generic to say two skills are related. */
    static final int MIN_HEAD = 6;

    /** Head words that end skills from unrelated fields, so sharing one says nothing. */
    private static final Set<String> GENERIC_HEADS = Set.of(
            "management", "development", "security", "analytics", "analysis", "review",
            "engineering", "design", "operations", "support", "testing", "planning", "service",
            "services", "solutions", "architecture", "administration", "processing",
            "optimization", "integration", "automation",
            "arbejde", "vurdering", "sikkerhed", "styring");

    private static final Pattern WORD_BREAK = Pattern.compile("[^\\p{L}\\p{N}-]+");

    /** Danish plural and linking endings, longest first, so "nyhedsbreve" meets "nyhedsbrevs". */
    private static final String[] ENDINGS = {"erne", "ene", "er", "e", "s"};

    private CompoundHeads() {}

    /** The stemmed last word of a label: the form {@link #related} compares. */
    static String headOf(String label) {
        String[] words = WORD_BREAK.split(label == null ? "" : label.toLowerCase(Locale.ROOT));
        return stem(words.length == 0 ? "" : words[words.length - 1]);
    }

    /** Whether two heads from {@link #headOf} name related skills. */
    static boolean related(String headA, String headB) {
        boolean aIsShorter = headA.length() <= headB.length();
        String shorter = aIsShorter ? headA : headB;
        String longer = aIsShorter ? headB : headA;
        if (shorter.length() < MIN_HEAD || GENERIC_HEADS.contains(shorter)) return false;
        return longer.startsWith(shorter) || longer.endsWith(shorter);
    }

    private static String stem(String word) {
        for (String ending : ENDINGS) {
            if (word.endsWith(ending) && word.length() - ending.length() >= MIN_HEAD) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }
}
