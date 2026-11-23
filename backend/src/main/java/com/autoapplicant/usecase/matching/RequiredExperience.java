package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.WorkExperience;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Specialist experience a posting demands that a work history clearly cannot meet: security
 * architecture, SOC work, penetration testing or software development. Four or more years of it
 * put a post out of reach ({@link #unmet}); a shorter ask only costs it some points
 * ({@link #shortfall}).
 *
 * <p>Skill labels cannot see this either. A comms profile with a GRC course shares most of a
 * technical security post's labels ("Sikkerhedsarkitektur", "Risikovurdering", "NIS2"), so
 * without this the post outranks her real fits.
 *
 * <p>This only ever lowers a score, so anything unclear counts as met:
 * <ul>
 *   <li>Only REQUIRED asks the enrichment kinds as EXPERIENCE and that say "erfaring",
 *       "experience" or similar count. "Kendskab til" is knowledge, which a course can give.
 *   <li>Only asks for {@link #SENIOR_YEARS} years or more rule a post out. Plain "erfaring med"
 *       or "2 års erfaring" is a normal ask that a graduate applies to on education alone, so
 *       it only costs a few points, fewer the closer the profile's skills come to the area.
 *   <li>Only areas in {@link #AREAS} count. "Erfaring med intern kommunikation" names nothing
 *       a work history can be checked against with any confidence. Incident response is left
 *       out like generic IT-sikkerhed: it is part of a GRC and crisis-comms field, learnt on
 *       courses rather than in a job title.
 *   <li>An ask that leaves a way in ("gerne", "en fordel", "eller lignende", "fx") counts as
 *       met, as for credentials. So does one naming an alternative outside the catalogue.
 *   <li>Any hands-on technical role meets an area's ask without naming the area: an engineer
 *       may well define security requirements, and only a history with no technical role at all
 *       clearly can't.
 *   <li>A years ask is met from half the years on, since postings' year counts are wishes as
 *       often as limits. A related role without dates meets any years ask.
 *   <li>A profile with no work history is unknown, not lacking.
 * </ul>
 */
final class RequiredExperience {

    /** The fewest years asked that make an ask senior enough to put a post out of reach. */
    private static final int SENIOR_YEARS = 4;

    /**
     * What a shorter ask in a lacked area costs when the profile's skills come as close to the
     * area as they can without the experience, and when nothing comes close. Asking for
     * experience is normal and a graduate applies on education alone, so this only reorders:
     * the security architect post must fall clearly below the mismatch ceiling for the comms
     * profile in {@code MatchScoreEvalTest}, and no good fit there may fall at all.
     *
     * <p>Closeness is read from skill labels only, the way adjacent skills are credited. A course
     * or role that names the area outright already meets the ask; one that only names something
     * nearby has no label to compare by meaning.
     */
    static final int NEAR_SHORTFALL_PENALTY = 5;
    static final int FAR_SHORTFALL_PENALTY = 12;

    /** Closeness from which the reason calls the profile's nearest skill related. */
    private static final double RELATED_FROM = 0.5;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Wording that makes an ask about experience. "Baggrund" and "background" are left out: on
     * their own they as often mean an education ("uddannelsesmæssig baggrund").
     */
    private static final Pattern EXPERIENCE_WORDING = Pattern.compile(
            "erfaring|erfaren|experience|experienced|track record"
                    + "|har arbejdet|have worked|has worked",
            FLAGS);

    /** "SOC" as a name, not the Danish abbreviation "soc." ("det soc. område"). */
    private static final String SOC = "\\bsoc\\b(?!\\.)";

    /**
     * Alternatives inside one ask. Unlike the credential gate, commas don't count: an experience
     * ask runs on in a clause ("sikkerhedsarkitektur, der understøtter forretningen").
     */
    private static final Pattern ALTERNATIVES = Pattern.compile("\\beller\\b|\\bor\\b|/", FLAGS);

    /**
     * A title that is technical work or works inside software delivery. Read on titles only: a
     * comms role describing "awareness-kampagner med it-afdelingen" is not a technical role.
     */
    private static final Pattern TECHNICAL_ROLE = Pattern.compile(
            "udvikler|developer|engineer|ingeniør|programmør|programmer|arkitekt|architect"
                    + "|devops|\\bsre\\b|\\bit-|\\bit\\b|sikkerhed|security|cyber|" + SOC
                    + "|pentest|systemadministrat|sysadmin|netværk|network|infrastruktur"
                    + "|infrastructure|product owner|scrum master|tech lead|\\bqa\\b|tester"
                    + "|\\bux\\b|data scientist",
            FLAGS);

    /**
     * Software development as the subject of an ask: having been a developer, or having developed
     * software. Working beside developers ("sammen med softwareudviklere") or in their team
     * ("softwareudviklingsteam", "in a software development team") is not, and must not cap a
     * comms or product profile.
     */
    private static final String DEVELOPED_SOFTWARE =
            "\\b(?:som|as an?)\\s+(?:\\w+\\s+){0,2}?"
                    + "(?:software|system|web|backend|frontend|full-?stack)?-?\\s?"
                    + "(?:udvikler|developer|engineer|programmør|programmer)\\b"
                    + "|\\b(?:med|with|in)\\s+(?:\\w+\\s+)?"
                    + "(?:softwareudvikling|software-?\\s?development)\\b"
                    + "(?!\\s*(?:team|miljø|afdeling|organisation|company|environment"
                    + "|department))"
                    + "|at udvikle software|developing software";

    /**
     * One specialist area: how an ask names it, what a role that did it mentions, the skill
     * label a profile's skills are compared with to see how close they come, and the experience
     * phrase in its reasons.
     */
    private record Area(Pattern named, Pattern held, String label, String phrase) {}

    private static final List<Area> AREAS = List.of(
            area("sikkerhedsarkitektur|security architecture", "Sikkerhedsarkitektur",
                    "erfaring med sikkerhedsarkitektur"),
            area(SOC + "|security operations cent", "SOC", "erfaring fra et SOC"),
            area("penetrationstest|penetration test|\\bpentest", "Penetrationstest",
                    "erfaring med penetrationstest"),
            new Area(Pattern.compile(DEVELOPED_SOFTWARE, FLAGS),
                    Pattern.compile("softwareudvikl|software(-| )?(udvikl|develop|engineer)"
                            + "|(backend|frontend|full-?stack|web)(-| )?(udvikl|develop)", FLAGS),
                    "Softwareudvikling", "erfaring som softwareudvikler"));

    /** What a shorter experience ask the history lacks costs, and the reason that says so. */
    record Shortfall(int penalty, String reason) {}

    private final List<WorkExperience> history;
    private final LocalDate today;

    private RequiredExperience(List<WorkExperience> history, LocalDate today) {
        this.history = history;
        this.today = today;
    }

    static RequiredExperience forHistory(List<WorkExperience> history, LocalDate today) {
        return new RequiredExperience(List.copyOf(history), today);
    }

    /** The labels {@link #shortfall} asks closeness for, to be compared by meaning up front. */
    static List<String> areaLabels() {
        return AREAS.stream().map(Area::label).toList();
    }

    /** Why {@code job} is out of reach for this work history, or empty when nothing clearly is. */
    Optional<String> unmet(Job job) {
        return experienceAsks(job)
                .filter(RequiredExperience::isSenior)
                .map(ask -> lackedAreas(ask).map(areas -> "Kræver "
                        + YearsAsked.in(ask).orElseThrow() + " års " + areas.get(0).phrase()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * What the shorter asks of {@code job} in areas this history lacks cost, empty when none
     * does. The penalty slides from {@link #FAR_SHORTFALL_PENALTY} down to
     * {@link #NEAR_SHORTFALL_PENALTY} as the profile comes closer to the area. A senior ask is
     * left to {@link #unmet}, which rules the post out instead.
     *
     * @param closeness how close the profile comes to a skill label, from 0 (nothing related)
     *                  to 1 (held, or related in full)
     */
    Optional<Shortfall> shortfall(Job job, ToDoubleFunction<String> closeness) {
        return experienceAsks(job)
                .filter(ask -> !isSenior(ask))
                .map(ask -> lackedAreas(ask).map(areas -> shortfallOf(areas, closeness)))
                .flatMap(Optional::stream)
                .max(Comparator.comparingInt(Shortfall::penalty));
    }

    private static Shortfall shortfallOf(List<Area> areas, ToDoubleFunction<String> closeness) {
        // Any of the alternatives would do, so the closest one counts.
        Area closest = areas.stream()
                .max(Comparator.comparingDouble(area -> closeness.applyAsDouble(area.label())))
                .orElseThrow();
        double close = Math.clamp(closeness.applyAsDouble(closest.label()), 0, 1);
        int penalty = (int) Math.round(FAR_SHORTFALL_PENALTY
                - (FAR_SHORTFALL_PENALTY - NEAR_SHORTFALL_PENALTY) * close);
        // A faint likeness, such as an unconfirmed suggestion, costs almost the full penalty and
        // must not be sold to her as related experience.
        String reason = close >= RELATED_FROM
                ? "Beder om " + closest.phrase() + "; du har noget beslægtet"
                : "Beder om " + closest.phrase() + ", som du ikke har endnu";
        return new Shortfall(penalty, reason);
    }

    /**
     * Required experience asks that offer no way in. Degree and skill asks are judged elsewhere:
     * read against a work history, a degree in software would cap every graduate who has not
     * worked as a developer.
     */
    private Stream<String> experienceAsks(Job job) {
        if (job.requirements() == null || history.isEmpty()) return Stream.empty();
        return job.requirements().stream()
                .filter(JobRequirement::isRequired)
                .filter(ask -> ask.kind() == RequirementKind.EXPERIENCE)
                .map(JobRequirement::text)
                .filter(Objects::nonNull)
                .filter(text -> EXPERIENCE_WORDING.matcher(text).find())
                .filter(text -> !HardRequirements.leavesAWayIn(text));
    }

    private static boolean isSenior(String ask) {
        return YearsAsked.in(ask).orElse(0) >= SENIOR_YEARS;
    }

    /**
     * The catalogued areas {@code ask} names, when this history meets none of them. Empty when
     * it meets one, or when an alternative outside the catalogue leaves a way in.
     */
    private Optional<List<Area>> lackedAreas(String ask) {
        // Below senior asks any related role meets the ask: a short stint in the area is closer
        // than a skill label, which never costs more than the near penalty.
        int years = isSenior(ask) ? YearsAsked.in(ask).orElseThrow() : 0;
        List<Optional<Area>> named = alternativesIn(ask);
        if (named.isEmpty() || named.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        List<Area> areas = named.stream().flatMap(Optional::stream).distinct().toList();
        if (areas.stream().anyMatch(area -> meets(area, years))) return Optional.empty();
        return Optional.of(areas);
    }

    /** The area each alternative in {@code ask} names, empty for one outside the catalogue. */
    private static List<Optional<Area>> alternativesIn(String ask) {
        // The year count goes first: "4 or more years" and "4 eller flere års" offer no
        // alternative, but would split into a part naming nothing and leave a way in.
        String areasAsked = YearsAsked.withoutCount(ask);
        return Arrays.stream(ALTERNATIVES.split(areasAsked))
                .filter(part -> !part.isBlank())
                .map(RequiredExperience::areaNamedIn)
                .toList();
    }

    private boolean meets(Area area, int years) {
        List<WorkExperience> related = history.stream().filter(role -> relates(role, area))
                .toList();
        if (related.isEmpty()) return false;
        if (related.stream().anyMatch(role -> role.startDate() == null)) return true;
        long months = related.stream().mapToLong(this::months).sum();
        // Half the years asked, in months: 4 years is met from 24 months.
        return months * 2 >= years * 12L;
    }

    private static boolean relates(WorkExperience role, Area area) {
        if (role.title() != null && TECHNICAL_ROLE.matcher(role.title()).find()) return true;
        return area.held().matcher(textOf(role)).find();
    }

    private long months(WorkExperience role) {
        LocalDate end = role.isCurrent() || role.endDate() == null ? today : role.endDate();
        return Math.max(0, ChronoUnit.MONTHS.between(role.startDate(), end));
    }

    private static String textOf(WorkExperience role) {
        Stream<String> listed = Stream.concat(
                role.technologies() == null ? Stream.empty() : role.technologies().stream(),
                role.skills() == null ? Stream.empty()
                        : role.skills().stream().map(SkillTaxonomy::name));
        return Stream.concat(Stream.of(role.title(), role.description()), listed)
                .filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }

    private static Optional<Area> areaNamedIn(String part) {
        return AREAS.stream().filter(area -> area.named().matcher(part).find()).findFirst();
    }

    private static Area area(String named, String label, String phrase) {
        Pattern pattern = Pattern.compile(named, FLAGS);
        return new Area(pattern, pattern, label, phrase);
    }
}
