package com.autoapplicant.domain.skill;

/**
 * The user's answer about one suggested skill.
 *
 * @param name             the skill as offered
 * @param decision         what they said
 * @param usedInProduction only meaningful for {@link Decision#YES}; the difference between having
 *                         touched something and having shipped with it
 * @param yearsExperience  optional, null when they did not say
 */
public record SkillConfirmation(String name, Decision decision,
                                boolean usedInProduction, Integer yearsExperience) {

    public enum Decision {
        /** Add it to the profile. */
        YES,
        /** Not mine — never offer it again. */
        NO,
        /** Ask me another time. */
        SKIP
    }
}
