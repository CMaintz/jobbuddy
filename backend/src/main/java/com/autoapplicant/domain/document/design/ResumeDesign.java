package com.autoapplicant.domain.document.design;

import java.util.List;
import java.util.Map;

/**
 * A CV as the resume builder lays it out: the content the user typed and the design choices
 * (layout, colours, font, spacing, section order) that decide how it looks. It is rendered as
 * sent; the client has already applied anonymisation and translated the headings, so nothing
 * here is looked up again.
 *
 * <p>Rich-text fields ({@code summary}, descriptions, custom section bodies) hold the builder's
 * editor HTML or plain text. Colours, fonts and sizes are unvalidated user input until the
 * renderer has checked them.
 *
 * @param layout one of the builder's templates (classic, modern, modern-2col, minimal,
 *     executive, creative)
 * @param pageSize {@code A4} or {@code Letter}
 * @param labels translated section headings, keyed as the builder names them
 * @param mainOrder main-column section ids in the user's order, hidden ones included
 * @param sideOrder side-column section ids in the user's order, hidden ones included
 * @param hidden section ids the user switched off
 */
public record ResumeDesign(
        String layout,
        String pageSize,
        Theme theme,
        Photo photo,
        boolean showSkillLevel,
        Map<String, String> labels,
        Person person,
        String summary,
        List<Entry> experience,
        List<Entry> education,
        List<Project> projects,
        List<SkillGroup> skillGroups,
        List<Language> languages,
        List<Certification> certifications,
        List<Strength> strengths,
        List<String> socials,
        List<CustomSection> customSections,
        List<String> mainOrder,
        List<String> sideOrder,
        List<String> hidden,
        Map<String, SectionStyle> sectionStyles) {

    /**
     * @param scale text size multiplier (1 = the layout's default)
     * @param lineSpacing unitless line height, e.g. 1.5
     * @param sectionSpacing gap between sections in the builder's CSS pixels
     */
    public record Theme(String color, String textColor, String font, Double scale, Double lineSpacing,
                        Double sectionSpacing) {}

    /**
     * @param size edge length in CSS pixels, or null for the layout's default
     * @param placement {@code left} or {@code right} of the name, where the layout has a header row
     */
    public record Photo(String url, String shape, Double size, String placement) {}

    public record Person(String name, String title, String email, String phone, String location) {}

    /** A job or an education; {@code end} is already "present" in the user's language when current. */
    public record Entry(String title, String subtitle, String location, String start, String end,
                        String description, List<String> skills) {}

    public record Project(String title, String date, String description, List<String> skills) {}

    /** @param category null for skills without a group */
    public record SkillGroup(String category, List<Skill> skills) {}

    /** @param level 1 to 5, or null when the user gave none */
    public record Skill(String name, Integer level) {}

    public record Language(String name, String proficiency) {}

    public record Certification(String name, String issuer, String date) {}

    public record Strength(String title, String description) {}

    public record CustomSection(String heading, String body, List<String> items) {}

    /** The builder's per-section overrides; every field is optional. */
    public record SectionStyle(String font, Boolean bold, Boolean italic, String color, Double scale) {}
}
