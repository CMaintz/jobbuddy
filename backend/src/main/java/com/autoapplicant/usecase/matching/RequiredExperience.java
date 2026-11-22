package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.skill.SkillTaxonomy;
import com.autoapplicant.domain.user.WorkExperience;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Senior specialist experience a posting demands that a work history clearly cannot meet: four
 * or more years of security architecture, SOC work, penetration testing or software development.
 *
 * <p>Skill labels cannot see this either. A comms profile with a GRC course shares most of a
 * technical security post's labels ("Sikkerhedsarkitektur", "Risikovurdering", "NIS2"), so
 * without this the post outranks her real fits.
 *
 * <p>This only ever lowers a score, so anything unclear counts as met:
 * <ul>
 *   <li>Only REQUIRED asks the enrichment kinds as EXPERIENCE and that say "erfaring",
 *       "experience" or similar count. "Kendskab til" is knowledge, which a course can give.
 *   <li>Only asks for {@link #SENIOR_YEARS} years or more count. Plain "erfaring med" or "2 års
 *       erfaring" is a normal ask that a graduate applies to on education alone.
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

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("en", 1), Map.entry("et", 1), Map.entry("one", 1),
            Map.entry("to", 2), Map.entry("two", 2), Map.entry("tre", 3), Map.entry("three", 3),
            Map.entry("fire", 4), Map.entry("four", 4), Map.entry("fem", 5),
            Map.entry("five", 5), Map.entry("seks", 6), Map.entry("six", 6),
            Map.entry("syv", 7), Map.entry("seven", 7), Map.entry("otte", 8),
            Map.entry("eight", 8), Map.entry("ni", 9), Map.entry("nine", 9),
            Map.entry("ti", 10), Map.entry("ten", 10));

    /** "3 års", "3+ years", "5-8 års", "fem års": the lowest number of years asked. */
    private static final Pattern YEARS = Pattern.compile(
            "\\b(\\d{1,2}|" + String.join("|", NUMBER_WORDS.keySet())
                    + ")\\s*\\+?\\s*(?:-\\s*\\d{1,2}\\s*)?(?:års|år|years?)\\b",
            FLAGS);

    /**
     * One specialist area: how an ask names it, what a role that did it mentions, and the
     * experience phrase in its reason.
     */
    private record Area(Pattern named, Pattern held, String phrase) {}

    private static final List<Area> AREAS = List.of(
            area("sikkerhedsarkitektur|security architecture",
                    "erfaring med sikkerhedsarkitektur"),
            area(SOC + "|security operations cent", "erfaring fra et SOC"),
            area("penetrationstest|penetration test|\\bpentest", "erfaring med penetrationstest"),
            new Area(Pattern.compile(DEVELOPED_SOFTWARE, FLAGS),
                    Pattern.compile("softwareudvikl|software(-| )?(udvikl|develop|engineer)"
                            + "|(backend|frontend|full-?stack|web)(-| )?(udvikl|develop)", FLAGS),
                    "erfaring som softwareudvikler"));

    private final List<WorkExperience> history;
    private final LocalDate today;

    private RequiredExperience(List<WorkExperience> history, LocalDate today) {
        this.history = history;
        this.today = today;
    }

    static RequiredExperience forHistory(List<WorkExperience> history, LocalDate today) {
        return new RequiredExperience(List.copyOf(history), today);
    }

    /** Why {@code job} is out of reach for this work history, or empty when nothing clearly is. */
    Optional<String> unmet(Job job) {
        if (job.requirements() == null || history.isEmpty()) return Optional.empty();
        return job.requirements().stream()
                .filter(JobRequirement::isRequired)
                // Degree and skill asks are judged elsewhere. Read against a work history, a
                // degree in software would cap every graduate who has not worked as a developer.
                .filter(ask -> ask.kind() == RequirementKind.EXPERIENCE)
                .map(JobRequirement::text)
                .filter(Objects::nonNull)
                .filter(text -> EXPERIENCE_WORDING.matcher(text).find())
                .filter(text -> !HardRequirements.leavesAWayIn(text))
                .filter(text -> yearsAsked(text).orElse(0) >= SENIOR_YEARS)
                .map(this::unmetBy)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * The reason {@code ask}, a senior ask, is out of reach: every alternative it names is an
     * area lacked.
     */
    private Optional<String> unmetBy(String ask) {
        int years = yearsAsked(ask).orElseThrow();
        List<Optional<Area>> named = Arrays.stream(ALTERNATIVES.split(ask))
                .filter(part -> !part.isBlank())
                .map(RequiredExperience::areaNamedIn)
                .toList();
        // An alternative outside the catalogue leaves a way in.
        if (named.stream().anyMatch(Optional::isEmpty)) return Optional.empty();
        List<Area> areas = named.stream().flatMap(Optional::stream).distinct().toList();
        if (areas.stream().anyMatch(area -> meets(area, years))) return Optional.empty();
        return Optional.of("Kræver " + years + " års " + areas.get(0).phrase());
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

    private static OptionalInt yearsAsked(String ask) {
        Matcher m = YEARS.matcher(ask);
        if (!m.find()) return OptionalInt.empty();
        String n = m.group(1).toLowerCase(Locale.ROOT);
        Integer word = NUMBER_WORDS.get(n);
        return OptionalInt.of(word != null ? word : Integer.parseInt(n));
    }

    private static Area area(String named, String phrase) {
        Pattern pattern = Pattern.compile(named, FLAGS);
        return new Area(pattern, pattern, phrase);
    }
}
