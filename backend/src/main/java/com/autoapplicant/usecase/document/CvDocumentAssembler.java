package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CvDocumentAssembler {

    private final AtsReportBuilder atsReportBuilder;

    public CvDocumentAssembler(AtsReportBuilder atsReportBuilder) {
        this.atsReportBuilder = atsReportBuilder;
    }

    public StructuredDocument assemble(User user, Profile profile, CareerProfileForAi source,
                                        TailoredCvContent tailored, String exportMode, String templateId,
                                        boolean showProfileImage, DocumentTheme theme) {
        List<String> selectedSkills = tailored != null && tailored.selectedSkills() != null && !tailored.selectedSkills().isEmpty()
                ? validateSkills(tailored.selectedSkills(), source)
                : merge(source.skills(), source.technologies());

        String selectedProfile = tailored != null && tailored.selectedProfile() != null && !tailored.selectedProfile().isBlank()
                ? tailored.selectedProfile()
                : source.profile();

        List<StructuredDocumentSection> sections = new ArrayList<>();
        if (selectedProfile != null && !selectedProfile.isBlank()) {
            sections.add(new StructuredDocumentSection("profile", "profile", "Profile", selectedProfile, List.of()));
        }
        if (!selectedSkills.isEmpty()) {
            sections.add(new StructuredDocumentSection("skills", "skills", "Skills", null,
                    selectedSkills.stream()
                            .map(skill -> new StructuredDocumentItem(null, skill, null, null, null, null, List.of(), List.of(), List.of(), List.of()))
                            .toList()));
        }
        addSection(sections, "experience", "experience", "Experience",
                validateItems(tailored != null ? tailored.experience() : null, source.experience()));
        addSection(sections, "projects", "projects", "Projects",
                validateItems(tailored != null ? tailored.projects() : null, source.projects()));
        addSection(sections, "education", "education", "Education",
                validateItems(tailored != null ? tailored.education() : null, source.education()));
        addSection(sections, "certifications", "certifications", "Certifications",
                validateItems(tailored != null ? tailored.certifications() : null, source.certifications()));
        if (source.spokenLanguages() != null && !source.spokenLanguages().isEmpty()) {
            sections.add(new StructuredDocumentSection("languages", "languages", "Languages", null,
                    source.spokenLanguages().stream()
                            .map(lang -> new StructuredDocumentItem(null, lang, null, null, null, null, List.of(), List.of(), List.of(), List.of()))
                            .toList()));
        }

        AtsReport report = tailored != null
                ? atsReportBuilder.forTailored(tailored, exportMode)
                : atsReportBuilder.basic(null, exportMode);

        return new StructuredDocument(
                null,
                DocumentType.CV,
                exportMode != null && !exportMode.isBlank() ? exportMode : "ATS",
                templateId,
                buildIdentity(user, profile),
                new DocumentRenderOptions(showProfileImage, theme),
                sections,
                null,
                report);
    }

    public DocumentIdentity buildIdentity(User user, Profile profile) {
        return new DocumentIdentity(
                profile != null ? profile.fullName() : "",
                profile != null ? profile.headline() : "",
                user != null ? user.email() : "",
                profile != null ? profile.phone() : "",
                profile != null ? profile.location() : "",
                profile != null ? profile.linkedinUrl() : "",
                profile != null ? profile.githubUrl() : "",
                profile != null ? profile.websiteUrl() : "",
                profile != null ? profile.photoUrl() : null);
    }

    private void addSection(List<StructuredDocumentSection> sections, String id, String type, String heading,
                             List<StructuredDocumentItem> items) {
        if (!items.isEmpty()) {
            sections.add(new StructuredDocumentSection(id, type, heading, null, items));
        }
    }

    private List<StructuredDocumentItem> validateItems(List<StructuredDocumentItem> tailored,
                                                        List<StructuredDocumentItem> source) {
        if (tailored == null || tailored.isEmpty()) {
            return source;
        }
        Map<String, StructuredDocumentItem> byId = source.stream()
                .filter(item -> item.sourceId() != null)
                .collect(Collectors.toMap(StructuredDocumentItem::sourceId, item -> item, (a, b) -> a));
        List<StructuredDocumentItem> validated = tailored.stream()
                .filter(item -> item.sourceId() != null && byId.containsKey(item.sourceId()))
                .map(item -> fillMissing(item, byId.get(item.sourceId())))
                .toList();
        return validated.isEmpty() ? source : validated;
    }

    private StructuredDocumentItem fillMissing(StructuredDocumentItem item, StructuredDocumentItem source) {
        return new StructuredDocumentItem(
                source.sourceId(),
                firstPresent(item.title(), source.title()),
                firstPresent(item.subtitle(), source.subtitle()),
                firstPresent(item.location(), source.location()),
                firstPresent(item.dateRange(), source.dateRange()),
                firstPresent(item.description(), source.description()),
                !listOrEmpty(item.bullets()).isEmpty() ? item.bullets() : source.bullets(),
                validateSubset(item.technologies(), source.technologies()),
                !listOrEmpty(item.links()).isEmpty() ? item.links() : source.links(),
                listOrEmpty(source.skills()));
    }

    private List<String> validateSkills(List<String> selected, CareerProfileForAi source) {
        Set<String> allowed = merge(source.skills(), source.technologies()).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> valid = selected.stream()
                .filter(Objects::nonNull)
                .filter(skill -> allowed.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        return valid.isEmpty() ? merge(source.skills(), source.technologies()) : valid;
    }

    private List<String> validateSubset(List<String> candidate, List<String> source) {
        if (candidate == null || candidate.isEmpty()) return listOrEmpty(source);
        Set<String> allowed = listOrEmpty(source).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> valid = candidate.stream()
                .filter(Objects::nonNull)
                .filter(value -> allowed.contains(value.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        return valid.isEmpty() ? listOrEmpty(source) : valid;
    }

    static String firstPresent(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    static List<String> listOrEmpty(List<String> values) {
        return values != null ? values : List.of();
    }

    static List<String> merge(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>();
        merged.addAll(first != null ? first : List.of());
        merged.addAll(second != null ? second : List.of());
        return merged.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
    }
}
