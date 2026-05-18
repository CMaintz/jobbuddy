package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.*;
import com.autoapplicant.port.out.ai.AiProviderPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StructuredDocumentService {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final JobRepositoryPort jobRepo;
    private final AiProviderPort aiProvider;
    private final ObjectMapper objectMapper;
    private final CareerProfileContextService careerProfileContext;

    public StructuredDocumentService(UserRepositoryPort userRepo,
                                     ProfileRepositoryPort profileRepo,
                                     JobRepositoryPort jobRepo,
                                     AiProviderPort aiProvider,
                                     ObjectMapper objectMapper,
                                     CareerProfileContextService careerProfileContext) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.jobRepo = jobRepo;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.careerProfileContext = careerProfileContext;
    }

    public StructuredDocument buildCv(UUID userId, String templateId) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = templateId != null && !templateId.isBlank() ? templateId : "cv-ats-classic";
        return buildCvDocument(userId, profile, source, null, exportModeFromTemplate(resolvedTemplate), resolvedTemplate);
    }

    /** @deprecated Use {@link #buildCv(UUID, String)} — exportMode is now derived from templateId */
    @Deprecated
    public StructuredDocument buildCv(UUID userId, String exportMode, String templateId) {
        String resolvedTemplate = templateId != null && !templateId.isBlank()
                ? templateId : defaultCvTemplate(exportMode);
        return buildCv(userId, resolvedTemplate);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId,
                                                       Integer keywordCoverage,
                                                       List<String> matchedKeywords,
                                                       List<String> missingKeywords) {
        String resolvedTemplate = templateId != null && !templateId.isBlank() ? templateId : "application-modern";
        AtsReport atsReport = keywordCoverage != null && keywordCoverage > 0
                ? new AtsReport(scoreFromCoverage(keywordCoverage), clamp(keywordCoverage),
                        listOrEmpty(matchedKeywords), listOrEmpty(missingKeywords),
                        cvChecks(exportModeFromTemplate(resolvedTemplate), List.of()))
                : basicAtsReport(content, exportModeFromTemplate(resolvedTemplate));
        return new StructuredDocument(
                type,
                exportModeFromTemplate(resolvedTemplate),
                resolvedTemplate,
                buildIdentity(userId, profileRepo.findByUserId(userId).orElse(null)),
                List.of(),
                content != null ? content : "",
                atsReport);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null);
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = templateId != null && !templateId.isBlank() ? templateId : "cv-ats-classic";
        String jobDescription = resolveJobDescription(jobId, rawJobDescription);
        TailoredCvContent tailored = requestTailoredCv(source, jobDescription, customInstructions, targetLanguage);
        return buildCvDocument(userId, profile, source, tailored, exportModeFromTemplate(resolvedTemplate), resolvedTemplate);
    }

    /** @deprecated Use {@link #generateTailoredCv(UUID, UUID, String, String, String, String)} */
    @Deprecated
    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String exportMode, String templateId) {
        String resolvedTemplate = templateId != null && !templateId.isBlank()
                ? templateId : defaultCvTemplate(exportMode);
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage, resolvedTemplate);
    }

    private TailoredCvContent requestTailoredCv(CareerProfileForAi source, String jobDescription,
                                                String customInstructions, String targetLanguage) {
        try {
            String sourceJson = objectMapper.writeValueAsString(source);
            String languageInstruction = targetLanguage != null && !targetLanguage.isBlank()
                    ? "Write all rewritten text in " + targetLanguage + "."
                    : "Write rewritten text in the same language as the job description when clear.";

            String prompt = """
                    You tailor CV content from a structured master career profile.
                    The profile intentionally excludes the user's name and contact information. Do not ask for it, infer it, or invent it.
                    Return only valid JSON matching this shape:
                    {
                      "selectedProfile": "role-specific profile text",
                      "selectedSkills": ["skill"],
                      "experience": [{"sourceId":"existing id","title":"...","subtitle":"...","location":"...","dateRange":"...","description":"...","bullets":["..."],"technologies":["..."],"links":[]}],
                      "projects": [],
                      "education": [],
                      "certifications": [],
                      "keywordCoverage": 0,
                      "matchedKeywords": [],
                      "missingKeywords": [],
                      "notes": []
                    }
                    Use only source facts. You may rewrite profile text, descriptions, and bullets, but must keep sourceId values from the provided profile.
                    Do not invent employers, titles, dates, schools, credentials, technologies, outcomes, or links.
                    """ + "\n" + languageInstruction + "\n\n## Contact-free master career profile JSON\n" + sourceJson +
                    "\n\n## Job description\n" + nullToEmpty(jobDescription) +
                    "\n\n## Additional instructions\n" + nullToEmpty(customInstructions);

            PromptComposition composition = new PromptComposition(
                    "You are a careful CV tailoring engine. Return JSON only.",
                    prompt, "", "", "", "", prompt);
            String json = aiProvider.generate(composition).trim();
            json = stripCodeFence(json);
            return objectMapper.readValue(json, TailoredCvContent.class);
        } catch (Exception e) {
            return new TailoredCvContent(
                    source.profile(),
                    merge(source.skills(), source.technologies()),
                    source.experience(),
                    source.projects(),
                    source.education(),
                    source.certifications(),
                    0,
                    List.of(),
                    List.of(),
                    List.of("AI tailoring failed, so the master profile was used without rewriting."));
        }
    }

    private StructuredDocument buildCvDocument(UUID userId, Profile profile, CareerProfileForAi source,
                                               TailoredCvContent tailored, String exportMode, String templateId) {
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

        AtsReport report = tailored != null
                ? new AtsReport(
                        scoreFromCoverage(tailored.keywordCoverage()),
                        clamp(tailored.keywordCoverage()),
                        listOrEmpty(tailored.matchedKeywords()),
                        listOrEmpty(tailored.missingKeywords()),
                        cvChecks(exportMode, tailored.notes()))
                : basicAtsReport(null, exportMode);

        return new StructuredDocument(
                DocumentType.CV,
                exportMode != null && !exportMode.isBlank() ? exportMode : "ATS",
                templateId != null && !templateId.isBlank() ? templateId : defaultCvTemplate(exportMode),
                buildIdentity(userId, profile),
                sections,
                null,
                report);
    }

    private void addSection(List<StructuredDocumentSection> sections, String id, String type, String heading,
                            List<StructuredDocumentItem> items) {
        if (!items.isEmpty()) {
            sections.add(new StructuredDocumentSection(id, type, heading, null, items));
        }
    }

    private DocumentIdentity buildIdentity(UUID userId, Profile profile) {
        User user = userRepo.findById(userId).orElse(null);
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
        Set<String> allowed = listOrEmpty(source).stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> valid = candidate.stream()
                .filter(Objects::nonNull)
                .filter(value -> allowed.contains(value.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        return valid.isEmpty() ? listOrEmpty(source) : valid;
    }

    private AtsReport basicAtsReport(String content, String exportMode) {
        List<AtsCheck> checks = cvChecks(exportMode, List.of());
        int wordCount = content != null && !content.isBlank() ? content.trim().split("\\s+").length : 0;
        int score = wordCount > 0 ? 72 : 80;
        return new AtsReport(score, 0, List.of(), List.of(), checks);
    }

    private List<AtsCheck> cvChecks(String exportMode, List<String> notes) {
        List<AtsCheck> checks = new ArrayList<>();
        checks.add(new AtsCheck("real_text", "Real text rendering", "PASS", "Rendered as selectable text rather than an image."));
        checks.add(new AtsCheck("standard_sections", "Standard CV headings", "PASS", "Uses predictable section headings for CV parsing."));
        checks.add(new AtsCheck("contact_privacy", "Contact privacy", "PASS", "Name and contact details are inserted after AI tailoring."));
        if ("DESIGNED".equalsIgnoreCase(exportMode)) {
            checks.add(new AtsCheck("layout_complexity", "Designed layout", "WARN", "Designed templates may parse less reliably than ATS mode."));
        }
        for (String note : listOrEmpty(notes)) {
            checks.add(new AtsCheck("ai_note", "Tailoring note", "INFO", note));
        }
        return checks;
    }

    private String resolveJobDescription(UUID jobId, String rawJobDescription) {
        if (jobId != null) {
            Optional<Job> job = jobRepo.findById(jobId);
            if (job.isPresent()) {
                return job.get().descriptionClean();
            }
        }
        return rawJobDescription;
    }

    private static String stripCodeFence(String value) {
        if (value.startsWith("```")) {
            int firstLine = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                return value.substring(firstLine + 1, lastFence).trim();
            }
        }
        return value;
    }

    private static String defaultCvTemplate(String exportMode) {
        return "DESIGNED".equalsIgnoreCase(exportMode) ? "cv-modern-professional" : "cv-ats-classic";
    }

    private static String exportModeFromTemplate(String templateId) {
        if (templateId == null) return "ATS";
        String lower = templateId.toLowerCase();
        return lower.contains("ats") ? "ATS" : "DESIGNED";
    }

    private static int scoreFromCoverage(Integer coverage) {
        int value = clamp(coverage);
        return Math.min(96, 65 + Math.round(value * 0.31f));
    }

    private static int clamp(Integer value) {
        if (value == null) return 0;
        return Math.max(0, Math.min(100, value));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String firstPresent(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private static List<String> listOrEmpty(List<String> values) {
        return values != null ? values : List.of();
    }

    private static List<String> merge(List<String> first, List<String> second) {
        List<String> merged = new ArrayList<>();
        merged.addAll(listOrEmpty(first));
        merged.addAll(listOrEmpty(second));
        return merged.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
    }
}
