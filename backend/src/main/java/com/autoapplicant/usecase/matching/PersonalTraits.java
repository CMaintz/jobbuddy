package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.skill.SkillNames;
import com.autoapplicant.usecase.skills.SkillCanonicalizer;
import java.util.Set;

/**
 * Personality traits a posting lists among its skills: "Struktur", "Samarbejde", "Team player".
 *
 * <p>Nearly every posting asks for a few, and a profile rarely lists them as skills, so they
 * counted as missing demands and pushed good fits down. One that a profile does list tells no
 * more: every applicant claims to be structured. A trait is therefore no ask at all in the skill
 * fit; the cover letter is where it belongs.
 *
 * <p>The list is explicit and matched on the whole label, never on part of a word:
 * "Bureausamarbejde" and "Forældresamarbejde" are skills of their own. A label that is also a
 * skill stays off the list ("Engagement" is a social media metric, "Ownership" a product role):
 * a stray trait costs less than a held skill no longer counting. The taxonomy's
 * {@code Soft Skill} category is no substitute, because it also holds Communication, Stakeholder
 * Management and Negotiation, which are the core of a communications profile.
 */
final class PersonalTraits {

    private static final Set<String> TRAITS = Set.of(
            // Danish
            "samarbejde", "samarbejdsevne", "samarbejdsevner", "samarbejdsvillig",
            "teamspiller", "holdspiller",
            "struktur", "struktureret", "struktureret arbejdsform", "struktur og overblik",
            "overblik", "systematisk",
            "initiativ", "initiativrig", "proaktiv",
            "selvstændig", "selvstændighed", "selvstændigt", "selvstændig opgaveløsning",
            "selvstændigt ansvar",
            "fleksibel", "fleksibilitet", "omstillingsparat", "omstillingsparathed",
            "engageret", "motiveret", "gåpåmod",
            "ansvarsbevidst", "ansvarsbevidsthed", "ansvarlig", "ansvarlighed",
            "pålidelig", "pålidelighed", "mødestabil", "loyal", "loyalitet",
            "servicemindet", "serviceminded", "servicemindedhed", "serviceorienteret",
            "serviceorientering",
            "detaljeorienteret", "detaljeorientering", "sans for detaljer", "grundig",
            "grundighed", "omhyggelig", "kvalitetsbevidst", "kvalitetsbevidsthed",
            "nysgerrig", "nysgerrighed", "positiv", "godt humør", "robust", "robusthed",
            "empati", "empatisk", "diskretion",
            // English; the taxonomy's own rows (Teamwork, Reliability, ...) fold onto these
            "teamwork", "team player", "collaboration", "collaborative", "cooperation",
            "structured", "organized", "organised",
            "initiative", "proactive", "proactivity", "self-starter", "self-motivated",
            "self-motivation", "independent", "independence",
            "flexible", "flexibility", "adaptable", "adaptability",
            "engaged", "motivated", "dedicated",
            "responsible", "accountability", "reliable", "reliability",
            "dependable",
            "service-minded", "service minded",
            "attention to detail", "detail-oriented", "detail oriented",
            "curious", "curiosity", "positive attitude", "empathy", "discretion");

    private PersonalTraits() {}

    /** Whether {@code label}, as written or under its taxonomy name, is a personality trait. */
    static boolean isTrait(String label, SkillCanonicalizer canonicalizer) {
        return TRAITS.contains(SkillNames.normalize(label))
                || TRAITS.contains(canonicalizer.canonical(label));
    }
}
