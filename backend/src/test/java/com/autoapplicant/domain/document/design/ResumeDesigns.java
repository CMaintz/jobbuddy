package com.autoapplicant.domain.document.design;

import java.util.List;
import java.util.Map;

/** Resume-builder designs for tests: a Danish CV with every section filled, and an empty one. */
public final class ResumeDesigns {

    public static final String NAME = "Mette Højgaard Sørensen";
    public static final String TITLE = "Kommunikationskonsulent";
    public static final String EXPERIENCE_HEADING = "Erfaring";
    public static final String SKILLS_HEADING = "Kompetencer";
    public static final String ROLE = "Projektkoordinator";
    public static final String BULLET = "Ledte omlægningen af kundekommunikationen";
    public static final String BOLD_PHRASE = "klare budskaber";
    public static final String SKILL = "Tekstforfatning";
    public static final String CUSTOM_ITEM = "Formand for grundejerforeningen";

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("summary", "Profil"), Map.entry("experience", EXPERIENCE_HEADING),
            Map.entry("professionalExperience", EXPERIENCE_HEADING), Map.entry("education", "Uddannelse"),
            Map.entry("projects", "Projekter"), Map.entry("skills", SKILLS_HEADING),
            Map.entry("coreSkills", SKILLS_HEADING), Map.entry("languages", "Sprog"),
            Map.entry("certifications", "Certificeringer"), Map.entry("strengths", "Styrker"),
            Map.entry("links", "Links"), Map.entry("contact", "Kontakt"));

    private ResumeDesigns() {}

    public static ResumeDesign danish(String layout) {
        return withPerson(layout, new ResumeDesign.Person(NAME, TITLE, "mette@example.dk", "+45 12 34 56 78", "Aarhus"),
                new ResumeDesign.Theme("#18324a", "#1f2937", "Inter", 1.0, 1.5, 20.0), null);
    }

    public static ResumeDesign withPerson(String layout, ResumeDesign.Person person, ResumeDesign.Theme theme,
                                          String photoUrl) {
        return new ResumeDesign(layout, "A4", theme, new ResumeDesign.Photo(photoUrl, "circle", null, null), true,
                LABELS, person,
                "<p>Erfaren kommunikationskonsulent med fokus på <strong>" + BOLD_PHRASE + "</strong>.</p>",
                List.of(new ResumeDesign.Entry("Kommunikationskonsulent", "Firma A/S", "Aarhus", "2019", "Nu",
                                "<ul><li>" + BULLET + " til klart sprog.</li><li>Skrev materiale til 400 kolleger.</li></ul>",
                                List.of("Compliance")),
                        new ResumeDesign.Entry(ROLE, "Kommune", "Randers", "2015", "2019",
                                "Koordinerede kampagner.", List.of())),
                List.of(new ResumeDesign.Entry("Cand.mag. i Kommunikation", "Aarhus Universitet", "Aarhus", "2010",
                        "2015", null, List.of())),
                List.of(new ResumeDesign.Project("Klart sprog-guide", "2022", "Intern guide.", List.of())),
                List.of(new ResumeDesign.SkillGroup(null, List.of(new ResumeDesign.Skill(SKILL, 5)))),
                List.of(new ResumeDesign.Language("Dansk", "Modersmål")),
                List.of(new ResumeDesign.Certification("Prince2 Foundation", "Axelos", "2021")),
                List.of(new ResumeDesign.Strength("Overblik", "Holder styr på mange bolde.")),
                List.of("linkedin.com/in/mette"),
                List.of(new ResumeDesign.CustomSection("Frivilligt arbejde", null, List.of(CUSTOM_ITEM))),
                List.of("summary", "experience", "education", "projects"),
                List.of("skills", "languages", "certifications", "strengths", "socials"),
                List.of(),
                Map.of());
    }

    public static ResumeDesign empty() {
        return new ResumeDesign("classic", "A4", null, null, false, Map.of(), null, null, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of());
    }
}
