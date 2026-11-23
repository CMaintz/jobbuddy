package com.autoapplicant.adapter.render;

import com.autoapplicant.adapter.render.TypstRichText.Block;
import com.autoapplicant.domain.document.design.ResumeDesign;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The JSON {@code resume.typ} reads; field names are its contract. Every design value is checked
 * here before it reaches Typst: an unknown layout falls back to classic, a malformed colour to
 * the default, an unbundled font to its stand-in, and numbers are clamped to what the builder's
 * own controls can produce. Text is ATS-normalized because the PDF text layer is what parsers read.
 */
record TypstResumeData(
        String layout,
        String page,
        Theme theme,
        Photo photo,
        boolean showSkillLevel,
        Map<String, String> labels,
        Person person,
        List<Block> summary,
        List<Entry> experience,
        List<Entry> education,
        List<Project> projects,
        List<SkillGroup> skillGroups,
        List<Language> languages,
        List<Certification> certifications,
        List<Strength> strengths,
        List<Social> socials,
        List<Custom> custom,
        List<String> main,
        List<String> side,
        List<String> hidden,
        Map<String, SectionStyle> sectionStyles) {

    private static final Set<String> LAYOUTS =
            Set.of("classic", "modern", "modern-2col", "minimal", "executive", "creative");
    private static final String DEFAULT_LAYOUT = "classic";
    private static final String DEFAULT_COLOR = "#18324a";
    private static final String DEFAULT_TEXT = "#1f2937";
    private static final Set<String> SHAPES = Set.of("circle", "rounded", "square");
    private static final Set<String> PLACEMENTS = Set.of("left", "right");
    private static final int MAX_LEVEL = 5;

    // The builder's own controls stay inside these; anything outside is crafted input.
    private static final Range SCALE = new Range(0.7, 1.5, 1);
    private static final Range LINE = new Range(1.0, 2.5, 1.5);
    private static final Range SECTION_GAP = new Range(0, 64, 20);
    private static final Range PHOTO = new Range(40, 200, 0);
    private static final Range SECTION_SCALE = new Range(0.5, 2, 1);

    record Theme(String color, String text, String font, double scale, double line, double sectionGap) {}

    /**
     * @param path the staged image inside the workspace root, or null for none
     * @param placement left or right of the name, or null for the layout's own side
     */
    record Photo(String path, String shape, Double size, String placement) {}

    record Person(String name, String title, String email, String phone, String location) {}

    record Entry(String title, String subtitle, String location, String start, String end,
                 List<Block> description, List<String> skills) {}

    record Project(String title, String date, List<Block> description, List<String> skills) {}

    record SkillGroup(String category, List<Skill> skills) {}

    record Skill(String name, Integer level) {}

    record Language(String name, String proficiency) {}

    record Certification(String name, String issuer, String date) {}

    record Strength(String title, String description) {}

    record Social(String label) {}

    record Custom(String heading, List<Block> body, List<String> items) {}

    record SectionStyle(String font, boolean bold, boolean italic, String color, double scale) {}

    private record Range(double min, double max, double fallback) {
        double clamp(Double value) {
            if (value == null || value.isNaN()) return fallback;
            return Math.max(min, Math.min(max, value));
        }
    }

    /** @param photoPath the staged photo's name inside the workspace root, or null */
    static TypstResumeData from(ResumeDesign design, String photoPath) {
        ResumeDesign.Theme theme = design.theme() != null
                ? design.theme()
                : new ResumeDesign.Theme(null, null, null, null, null, null);
        ResumeDesign.Photo photo = design.photo() != null ? design.photo() : new ResumeDesign.Photo(null, null, null, null);
        ResumeDesign.Person person = design.person() != null
                ? design.person()
                : new ResumeDesign.Person(null, null, null, null, null);
        return new TypstResumeData(
                oneOf(design.layout(), LAYOUTS, DEFAULT_LAYOUT),
                "Letter".equalsIgnoreCase(design.pageSize()) ? "letter" : "a4",
                new Theme(
                        colorOr(theme.color(), DEFAULT_COLOR),
                        colorOr(theme.textColor(), DEFAULT_TEXT),
                        TypstFonts.bundled(theme.font(), TypstFonts.INTER),
                        SCALE.clamp(theme.scale()),
                        LINE.clamp(theme.lineSpacing()),
                        SECTION_GAP.clamp(theme.sectionSpacing())),
                new Photo(photoPath, oneOf(photo.shape(), SHAPES, "circle"),
                        photo.size() != null ? PHOTO.clamp(photo.size()) : null,
                        oneOf(photo.placement(), PLACEMENTS, null)),
                design.showSkillLevel(),
                labels(design.labels()),
                new Person(text(person.name()), text(person.title()), text(person.email()), text(person.phone()),
                        text(person.location())),
                TypstRichText.parse(design.summary()),
                map(design.experience(), TypstResumeData::entry),
                map(design.education(), TypstResumeData::entry),
                map(design.projects(), p -> new Project(text(p.title()), text(p.date()),
                        TypstRichText.parse(p.description()), texts(p.skills()))),
                map(design.skillGroups(), g -> new SkillGroup(blankToNull(g.category()),
                        map(g.skills(), s -> new Skill(text(s.name()), level(s.level()))))),
                map(design.languages(), l -> new Language(text(l.name()), text(l.proficiency()))),
                map(design.certifications(), c -> new Certification(text(c.name()), text(c.issuer()), text(c.date()))),
                map(design.strengths(), s -> new Strength(text(s.title()), text(s.description()))),
                texts(design.socials()).stream().map(Social::new).toList(),
                map(design.customSections(), c -> new Custom(text(c.heading()), TypstRichText.parse(c.body()),
                        texts(c.items()))),
                texts(design.mainOrder()),
                texts(design.sideOrder()),
                texts(design.hidden()),
                sectionStyles(design.sectionStyles()));
    }

    private static Entry entry(ResumeDesign.Entry e) {
        return new Entry(text(e.title()), text(e.subtitle()), text(e.location()), text(e.start()), text(e.end()),
                TypstRichText.parse(e.description()), texts(e.skills()));
    }

    /** Headings come from the client's translations, so they are plain text like any other field. */
    private static Map<String, String> labels(Map<String, String> labels) {
        Map<String, String> out = new LinkedHashMap<>();
        if (labels != null) labels.forEach((key, value) -> out.put(key, text(value)));
        return out;
    }

    private static Map<String, SectionStyle> sectionStyles(Map<String, ResumeDesign.SectionStyle> styles) {
        Map<String, SectionStyle> out = new LinkedHashMap<>();
        if (styles == null) return out;
        styles.forEach((id, s) -> {
            if (s == null) return;
            out.put(id, new SectionStyle(
                    s.font() != null ? TypstFonts.bundled(s.font(), null) : null,
                    Boolean.TRUE.equals(s.bold()),
                    Boolean.TRUE.equals(s.italic()),
                    TypstRichText.color(s.color()),
                    SECTION_SCALE.clamp(s.scale())));
        });
        return out;
    }

    /** {@code value} when it is one of {@code allowed}; Set.of rejects a null lookup, so check first. */
    private static String oneOf(String value, Set<String> allowed, String fallback) {
        return value != null && allowed.contains(value) ? value : fallback;
    }

    private static Integer level(Integer level) {
        return level == null || level < 1 ? null : Math.min(level, MAX_LEVEL);
    }

    private static String colorOr(String value, String fallback) {
        String color = TypstRichText.color(value);
        return color != null ? color : fallback;
    }

    private static <T, R> List<R> map(List<T> values, Function<T, R> mapper) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(mapper).toList();
    }

    private static List<String> texts(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(TypstResumeData::text).toList();
    }

    private static String text(String value) {
        return Objects.requireNonNullElse(AtsText.normalize(value), "").strip();
    }

    private static String blankToNull(String value) {
        String text = text(value);
        return text.isEmpty() ? null : text;
    }
}
