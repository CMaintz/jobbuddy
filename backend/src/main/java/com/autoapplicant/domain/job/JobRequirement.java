package com.autoapplicant.domain.job;

/**
 * One thing a posting asks for, in the posting's own words.
 *
 * <p>Distinct from {@code requiredSkills}/{@code preferredSkills}, which are narrowed to
 * labels the posting also listed as a technology or skill. That narrowing protects the
 * matcher — an invented requirement must not penalise the candidate — but it silently
 * drops every ask that is not a short label, which is most of what a posting actually
 * says. Those two lists still feed scoring; this one feeds tailoring, where a dropped
 * requirement is a requirement the CV never answers.
 *
 * @param text  the ask as written, e.g. "5 års erfaring med backend-udvikling"
 * @param skill the matching short label when this is a nameable skill, else null. Lets a
 *              SKILL requirement join up with the keyword check without re-deriving it.
 */
public record JobRequirement(
        String text,
        RequirementTier tier,
        RequirementKind kind,
        String skill
) {
    public JobRequirement {
        tier = tier == null ? RequirementTier.PREFERRED : tier;
        kind = kind == null ? RequirementKind.OTHER : kind;
    }

    public boolean isRequired() {
        return tier == RequirementTier.REQUIRED;
    }
}
