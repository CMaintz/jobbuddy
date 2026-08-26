package com.autoapplicant.domain.document.structured;

import java.util.List;

public record CareerProfileForAi(
        String headline,
        String profile,
        List<String> skills,
        List<String> technologies,
        List<String> languages,
        List<String> spokenLanguages,
        List<StructuredDocumentItem> experience,
        List<StructuredDocumentItem> projects,
        List<StructuredDocumentItem> education,
        List<StructuredDocumentItem> certifications,
        List<String> strengths,
        /** Declared target role-archetypes to frame generation toward. Identity-free. */
        List<String> targetArchetypes,
        /** One-line statement of the ideal next role / direction. */
        String northStar,
        /** Positioning narrative distinct from the CV summary. */
        String narrative,
        /** Self-declared career stage (e.g. NEW_GRAD, SENIOR) driving stage-appropriate framing. */
        String careerStage,
        /** Skill name → category (e.g. "Java" → "Languages"), so the CV can group skills. */
        java.util.Map<String, String> skillCategories
) {}
