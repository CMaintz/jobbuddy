package com.autoapplicant.usecase.matching;

import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobRequirement;
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
 * Specialist experience a posting demands that a work history clearly cannot meet: years of
 * security architecture, incident response, SOC work, penetration testing or software
 * development.
 *
 * <p>Skill labels cannot see this either. A comms profile with a GRC course shares most of a
 * technical security post's labels ("Sikkerhedsarkitektur", "Risikovurdering", "NIS2"), so
 * without this the post outranks her real fits.
 *
 * <p>This only ever lowers a score, so anything unclear counts as met:
 * <ul>
 *   <li>Only REQUIRED asks that say "erfaring", "experience" or similar count. "Kendskab til"
 *       is knowledge, which a course can give.
 *   <li>Only areas in {@link #AREAS} count. "Erfaring med intern kommunikation" names nothing
 *       a work history can be checked against with any confidence.
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

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern EXPERIENCE_WORDING = Pattern.compile(
            "erfaring|erfaren|experience|experienced|baggrund|background|track record"
                    + "|har arbejdet|have worked|has worked",
            FLAGS);

    /**
     * Alternatives inside one ask. Unlike the credential gate, commas don't count: an experience
     * ask runs on in a clause ("sikkerhedsarkitektur, der understøtter forretningen").
     */
    private static final Pattern ALTERNATIVES = Pattern.compile("\\beller\\b|\\bor\\b|/", FLAGS);

    /**
     * A title that is hands-on technical work. Read on titles only: a comms role describing
     * "awareness-kampagner med it-afdelingen" is not a technical role.
     */
    private static final Pattern TECHNICAL_ROLE = Pattern.compile(
            "udvikler|developer|engineer|ingeniør|programmør|programmer|arkitekt|architect"
                    + "|devops|\\bsre\\b|\\bit-|\\bit\\b|sikkerhed|security|cyber|\\bsoc\\b"
                    + "|pentest|systemadministrat|sysadmin|netværk|network|infrastruktur"
                    + "|infrastructure",
            FLAGS);

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

    /** One specialist area: how an ask names it, and the experience phrase in its reason. */
    private record Area(Pattern named, String phrase) {}

    private static final List<Area> AREAS = List.of(
            area("sikkerhedsarkitektur|security architecture",
                    "erfaring med sikkerhedsarkitektur"),
            area("incident response|håndtering af (it-)?sikkerhedshændelser",
                    "erfaring med incident response"),
            area("\\bsoc\\b|security operations cent", "erfaring fra et SOC"),
            area("penetrationstest|penetration test|\\bpentest", "erfaring med penetrationstest"),
            area("softwareudvikl|software(-| )?(udvikl|develop|engineer)"
                    + "|(backend|frontend|full-?stack|web)(-| )?(udvikl|develop)",
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
                .map(JobRequirement::text)
                .filter(Objects::nonNull)
                .filter(text -> EXPERIENCE_WORDING.matcher(text).find())
                .filter(text -> !HardRequirements.leavesAWayIn(text))
                .map(this::unmetBy)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** The reason {@code ask} is out of reach: every alternative it names is an area lacked. */
    private Optional<String> unmetBy(String ask) {
        List<Optional<Area>> named = Arrays.stream(ALTERNATIVES.split(ask))
                .filter(part -> !part.isBlank())
                .map(RequiredExperience::areaNamedIn)
                .toList();
        // An alternative outside the catalogue leaves a way in.
        if (named.stream().anyMatch(Optional::isEmpty)) return Optional.empty();
        OptionalInt years = yearsAsked(ask);
        List<Area> areas = named.stream().flatMap(Optional::stream).distinct().toList();
        if (areas.stream().anyMatch(area -> meets(area, years))) return Optional.empty();
        String prefix = years.isPresent() ? years.getAsInt() + " års " : "";
        return Optional.of("Kræver " + prefix + areas.get(0).phrase());
    }

    private boolean meets(Area area, OptionalInt years) {
        List<WorkExperience> related = history.stream().filter(role -> relates(role, area))
                .toList();
        if (related.isEmpty()) return false;
        if (years.isEmpty()) return true;
        if (related.stream().anyMatch(role -> role.startDate() == null)) return true;
        long months = related.stream().mapToLong(this::months).sum();
        // Half the years asked, in months: 3 years is met from 18 months.
        return months * 2 >= years.getAsInt() * 12L;
    }

    private static boolean relates(WorkExperience role, Area area) {
        if (role.title() != null && TECHNICAL_ROLE.matcher(role.title()).find()) return true;
        return area.named().matcher(textOf(role)).find();
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
        return new Area(Pattern.compile(named, FLAGS), phrase);
    }
}
