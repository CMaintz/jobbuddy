package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.domain.user.User;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CvDocumentAssembler {

    /** Upper bound on rendered skills — keeps the section scannable and ATS-parseable. */
    private static final int MAX_SKILLS = 24;

    private final AtsReportBuilder atsReportBuilder;

    public CvDocumentAssembler(AtsReportBuilder atsReportBuilder) {
        this.atsReportBuilder = atsReportBuilder;
    }

    public StructuredDocument assemble(User user, Profile profile, ProfilePrivateInfo privateInfo,
                                        List<ProfileSocial> socials, CareerProfileForAi source,
                                        TailoredCvContent tailored, String exportMode, String templateId,
                                        boolean showProfileImage, DocumentTheme theme,
                                        ContentGuardFindings guardFindings,
                                        String documentLanguage) {
        CvSectionLabels labels = CvSectionLabels.forLanguage(documentLanguage);
        List<String> selectedSkills = tailored != null && tailored.selectedSkills() != null && !tailored.selectedSkills().isEmpty()
                ? validateSkills(tailored.selectedSkills(), source)
                : merge(source.skills(), source.technologies());

        String selectedProfile = tailored != null && tailored.selectedProfile() != null && !tailored.selectedProfile().isBlank()
                ? tailored.selectedProfile()
                : source.profile();

        List<StructuredDocumentSection> sections = new ArrayList<>();
        if (selectedProfile != null && !selectedProfile.isBlank()) {
            sections.add(new StructuredDocumentSection("profile", "profile", labels.profile(), selectedProfile, List.of()));
        }
        if (!selectedSkills.isEmpty()) {
            Map<String, String> skillCategory = categoryLookup(source.skillCategories());
            sections.add(new StructuredDocumentSection("skills", "skills", labels.skills(), null,
                    selectedSkills.stream()
                            .map(skill -> new StructuredDocumentItem(null, skill, null, null, null, null,
                                    List.of(), List.of(), List.of(), List.of(),
                                    skillCategory.get(skill.toLowerCase(Locale.ROOT))))
                            .toList()));
        }
        // Career-stage drives the default order of the three "story" sections. Early-stage
        // candidates (student/new grad) lead with education + projects; everyone else leads with
        // experience. This order is authoritative: the frontend derives the resume-builder's
        // default column layout from it (the user can still reorder in the builder).
        List<StructuredDocumentItem> experienceItems =
                validateItems(tailored != null ? tailored.experience() : null, source.experience());
        List<StructuredDocumentItem> projectItems =
                validateItems(tailored != null ? tailored.projects() : null, source.projects());
        List<StructuredDocumentItem> educationItems =
                validateItems(tailored != null ? tailored.education() : null, source.education());
        if (isEarlyStage(source.careerStage())) {
            addSection(sections, "education", "education", labels.education(), educationItems);
            addSection(sections, "projects", "projects", labels.projects(), projectItems);
            addSection(sections, "experience", "experience", labels.experience(), experienceItems);
        } else {
            addSection(sections, "experience", "experience", labels.experience(), experienceItems);
            addSection(sections, "projects", "projects", labels.projects(), projectItems);
            addSection(sections, "education", "education", labels.education(), educationItems);
        }
        addSection(sections, "certifications", "certifications", labels.certifications(),
                validateItems(tailored != null ? tailored.certifications() : null, source.certifications()));
        if (source.spokenLanguages() != null && !source.spokenLanguages().isEmpty()) {
            sections.add(new StructuredDocumentSection("languages", "languages", labels.languages(), null,
                    source.spokenLanguages().stream()
                            .map(lang -> new StructuredDocumentItem(null, lang, null, null, null, null, List.of(), List.of(), List.of(), List.of(), null))
                            .toList()));
        }

        // Fritidsinteresser: a standard closing section on a Danish CV, rendered verbatim from the
        // user's own data — never AI-selected, since there is nothing to tailor about a hobby.
        if (source.interests() != null && !source.interests().isEmpty()) {
            sections.add(new StructuredDocumentSection("interests", "interests", labels.interests(), null,
                    source.interests().stream()
                            .map(interest -> new StructuredDocumentItem(null, interest, null, null, null,
                                    null, List.of(), List.of(), List.of(), List.of(), null))
                            .toList()));
        }
        String referencesNote = labels.referencesNote();
        if (referencesNote != null) {
            sections.add(new StructuredDocumentSection("references", "references", labels.references(),
                    referencesNote, List.of()));
        }

        ContentGuardFindings findings = guardFindings != null ? guardFindings : ContentGuardFindings.NONE;
        AtsReport report = tailored != null
                ? atsReportBuilder.forTailored(tailored, exportMode, findings)
                : atsReportBuilder.basic(null, exportMode, findings);

        return new StructuredDocument(
                null,
                DocumentType.CV,
                exportMode != null && !exportMode.isBlank() ? exportMode : "ATS",
                templateId,
                buildIdentity(user, profile, privateInfo, socials),
                new DocumentRenderOptions(showProfileImage, theme),
                sections,
                null,
                report);
    }

    public DocumentIdentity buildIdentity(User user, Profile profile,
                                           ProfilePrivateInfo privateInfo, List<ProfileSocial> socials) {
        String linkedinUrl = socials != null ? socials.stream()
                .filter(s -> "linkedin".equalsIgnoreCase(s.iconKey())).findFirst()
                .map(ProfileSocial::url).orElse("") : "";
        String githubUrl = socials != null ? socials.stream()
                .filter(s -> "github".equalsIgnoreCase(s.iconKey())).findFirst()
                .map(ProfileSocial::url).orElse("") : "";
        String websiteUrl = socials != null ? socials.stream()
                .filter(s -> "globe".equalsIgnoreCase(s.iconKey())).findFirst()
                .map(ProfileSocial::url).orElse("") : "";
        return new DocumentIdentity(
                privateInfo != null ? privateInfo.fullName() : "",
                profile != null ? profile.headline() : "",
                privateInfo != null && privateInfo.contactEmail() != null
                        ? privateInfo.contactEmail()
                        : (user != null ? user.email() : ""),
                privateInfo != null ? privateInfo.phone() : "",
                privateInfo != null ? privateInfo.location() : "",
                linkedinUrl,
                githubUrl,
                websiteUrl,
                privateInfo != null ? privateInfo.photoUrl() : null);
    }

    /** Convenience overload for callers that don't have split profile info yet. */
    public DocumentIdentity buildIdentity(User user, Profile profile) {
        return buildIdentity(user, profile, null, List.of());
    }

    /** Student / new-grad → education + projects should precede experience by default. */
    private static boolean isEarlyStage(String careerStage) {
        return "STUDENT".equals(careerStage) || "NEW_GRAD".equals(careerStage);
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
                listOrEmpty(source.skills()),
                firstPresent(item.category(), source.category()));
    }

    /** Lowercased skill-name → category map for case-insensitive lookup; empty when none declared. */
    private static Map<String, String> categoryLookup(Map<String, String> skillCategories) {
        if (skillCategories == null || skillCategories.isEmpty()) return Map.of();
        Map<String, String> lookup = new HashMap<>();
        skillCategories.forEach((name, category) -> {
            if (name != null && category != null && !category.isBlank()) {
                lookup.put(name.toLowerCase(Locale.ROOT), category);
            }
        });
        return lookup;
    }

    /**
     * Skills the AI selected (keyword-first, most relevant to the posting leading), validated
     * against the master profile and capped so the section scans rather than becoming a wall.
     * The AI's ordering is preserved; the cap simply drops the long tail of least-relevant skills.
     */
    private List<String> validateSkills(List<String> selected, CareerProfileForAi source) {
        Set<String> allowed = merge(source.skills(), source.technologies()).stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> valid = selected.stream()
                .filter(Objects::nonNull)
                .filter(skill -> allowed.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        List<String> resolved = valid.isEmpty() ? merge(source.skills(), source.technologies()) : valid;
        return resolved.size() > MAX_SKILLS ? resolved.subList(0, MAX_SKILLS) : resolved;
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
