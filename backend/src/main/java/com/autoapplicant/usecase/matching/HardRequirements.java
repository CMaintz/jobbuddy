package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.skill.ProfileSkill;
import com.autoapplicant.domain.user.Certification;
import com.autoapplicant.domain.user.Education;
import com.autoapplicant.domain.user.Profile;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The must-haves a posting demands that a profile clearly does not hold: a degree from a named
 * field, a named certification, or a security clearance the candidate must already have.
 *
 * <p>Skill labels cannot see these. A comms profile with a GRC course matches most of a
 * cand.jur. GDPR post's asks, so without this the post outranks real fits.
 *
 * <p>This only ever lowers a score, so anything unclear counts as satisfied:
 * <ul>
 *   <li>Only asks the enrichment tiered REQUIRED count.
 *   <li>Only credentials in {@link #CREDENTIALS} count. "En relevant uddannelse" or a list of
 *       fields names nothing a profile can be checked against.
 *   <li>An ask that leaves a way in ("eller tilsvarende", "gerne", "or equivalent") counts as
 *       met. So does a clearance the candidate only has to be able to obtain ("kan
 *       sikkerhedsgodkendes"), which is how nearly every Danish posting words it.
 *   <li>A profile that records no education is unknown, not lacking, for a degree. One that
 *       records neither education nor certifications is unknown for everything.
 * </ul>
 */
final class HardRequirements {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern OPEN_ENDED = Pattern.compile(
            "lignende|\\blign\\b|tilsvarende|anden relevant|andet relevant|alternativt|\\bgerne\\b"
                    + "|fordel|\\bevt\\b|or similar|equivalent|nice to have|\\bplus\\b"
                    + "|praktisk erfaring|practical experience"
                    + "|\\bfx\\b|f\\.\\s?eks|eksempelvis|e\\.g\\.|for example", FLAGS);

    private static final Pattern NEVER = Pattern.compile("(?!)");

    private static final Set<RequirementKind> DEGREE_KINDS = EnumSet.of(RequirementKind.EDUCATION);
    private static final Set<RequirementKind> CREDENTIAL_KINDS = EnumSet.of(
            RequirementKind.EDUCATION, RequirementKind.CERTIFICATION, RequirementKind.OTHER);

    /**
     * One credential: the reason shown when it is missing, what an ask demanding it reads like,
     * what holding it looks like in a profile, and wording that turns the demand into something
     * the candidate does not need yet. Held patterns match stems, because profiles name the same
     * degree in many ways ("Sygepleje", "Erhvervsjuridisk").
     */
    private record Credential(String reason, boolean isDegree, Set<RequirementKind> kinds,
                              Pattern demand, Pattern held, Pattern unless) {

        boolean unmetBy(JobRequirement ask, String profileText) {
            String text = ask.text();
            return kinds.contains(ask.kind()) && demand.matcher(text).find()
                    && !unless.matcher(text).find() && !OPEN_ENDED.matcher(text).find()
                    && !held.matcher(profileText).find();
        }
    }

    private static final String LAW = "cand\\.?\\s?(merc\\.?\\s?)?jur\\b|\\bjurist"
            + "|juridisk (kandidat|uddannelse)|master of laws|\\bll\\.?m\\b";
    private static final String CLEARANCE = "sikkerhedsgodkend|security clearance";
    /** Only a clearance held when applying counts; anyone may pass vetting after hire. */
    private static final String HELD_CLEARANCE = "\\b(har|have|has|holds?|gyldig|aktiv|active"
            + "|valid|current)\\b.{0,20}(" + CLEARANCE + ")";

    private static final List<Credential> CREDENTIALS = List.of(
            degree("Kræver cand.jur.", LAW, LAW + "|\\bjur|\\blaw\\b|erhvervsjur|advokat"),
            degree("Kræver en pædagoguddannelse", "\\bpædagog(\\b|uddannel)", "\\bpædagog"),
            degree("Kræver en sygeplejerskeuddannelse", "sygeplejerske", "sygeplej|\\bnurs"),
            degree("Kræver en tømrer- eller snedkeruddannelse", "tømrer|snedker", "tømrer|snedker"),
            certification("CISSP"), certification("CISM"), certification("CISA"),
            certification("CRISC"), certification("OSCP"), certification("CIPP"),
            certification("CIPM"), certification("PRINCE2"), certification("PMP"),
            certification("Lead Auditor"), certification("Lead Implementer"),
            new Credential("Kræver sikkerhedsgodkendelse", false, CREDENTIAL_KINDS,
                    Pattern.compile(HELD_CLEARANCE, FLAGS), Pattern.compile(CLEARANCE, FLAGS),
                    Pattern.compile("\\b(kan|kunne|opnå|blive|bliver|able|obtain|eligible)\\b",
                            FLAGS)));

    private final boolean educationKnown;
    private final boolean credentialsKnown;
    private final String profileText;

    private HardRequirements(boolean educationKnown, boolean credentialsKnown,
                             String profileText) {
        this.educationKnown = educationKnown;
        this.credentialsKnown = credentialsKnown;
        this.profileText = profileText;
    }

    static HardRequirements forProfile(Profile profile, List<ProfileSkill> skills,
                                       List<Education> education,
                                       List<Certification> certifications) {
        String text = Stream.of(
                        Stream.of(profile.headline(), profile.summary()),
                        skills.stream().map(ProfileSkill::skillName),
                        education.stream().flatMap(e -> Stream.of(e.degree(), e.fieldOfStudy())),
                        certifications.stream().map(Certification::name))
                .flatMap(s -> s)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .toLowerCase(Locale.ROOT);
        return new HardRequirements(!education.isEmpty(),
                !education.isEmpty() || !certifications.isEmpty(), text);
    }

    /** Why {@code job} is out of reach for this profile, or empty when nothing clearly is. */
    Optional<String> unmet(Job job) {
        if (job.requirements() == null) return Optional.empty();
        return job.requirements().stream()
                .filter(JobRequirement::isRequired)
                .filter(ask -> ask.text() != null)
                .flatMap(ask -> CREDENTIALS.stream()
                        .filter(c -> knows(c) && c.unmetBy(ask, profileText)))
                .map(Credential::reason)
                .findFirst();
    }

    private boolean knows(Credential credential) {
        return credential.isDegree() ? educationKnown : credentialsKnown;
    }

    private static Credential degree(String reason, String demand, String held) {
        return new Credential(reason, true, DEGREE_KINDS, Pattern.compile(demand, FLAGS),
                Pattern.compile(held, FLAGS), NEVER);
    }

    private static Credential certification(String name) {
        Pattern named = Pattern.compile("\\b" + Pattern.quote(name) + "\\b", FLAGS);
        return new Credential("Kræver " + name, false, CREDENTIAL_KINDS, named, named, NEVER);
    }
}
