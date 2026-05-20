package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StructuredDocumentService {

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final JobRepositoryPort jobRepo;
    private final CareerProfileContextService careerProfileContext;
    private final CvDocumentAssembler cvAssembler;
    private final TailoredCvGenerator tailoredCvGenerator;
    private final AtsReportBuilder atsReportBuilder;

    public StructuredDocumentService(UserRepositoryPort userRepo,
                                     ProfileRepositoryPort profileRepo,
                                     JobRepositoryPort jobRepo,
                                     CareerProfileContextService careerProfileContext,
                                     CvDocumentAssembler cvAssembler,
                                     TailoredCvGenerator tailoredCvGenerator,
                                     AtsReportBuilder atsReportBuilder) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.jobRepo = jobRepo;
        this.careerProfileContext = careerProfileContext;
        this.cvAssembler = cvAssembler;
        this.tailoredCvGenerator = tailoredCvGenerator;
        this.atsReportBuilder = atsReportBuilder;
    }

    public StructuredDocument buildCv(UUID userId, String templateId) {
        return buildCv(userId, templateId, false);
    }

    public StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage) {
        return buildCv(userId, templateId, showProfileImage, DocumentTheme.defaults());
    }

    public StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage, DocumentTheme theme) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        User user = userRepo.findById(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = resolveTemplate(templateId, "cv-ats-classic");
        return cvAssembler.assemble(user, profile, source, null,
                exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, resolveTheme(theme));
    }

    /** @deprecated Use {@link #buildCv(UUID, String)} — exportMode is now derived from templateId */
    @Deprecated
    public StructuredDocument buildCv(UUID userId, String exportMode, String templateId) {
        String resolvedTemplate = templateId != null && !templateId.isBlank()
                ? templateId : defaultCvTemplate(exportMode);
        return buildCv(userId, resolvedTemplate, false);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null, false);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId, boolean showProfileImage) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null, showProfileImage);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId, boolean showProfileImage,
                                                       DocumentTheme theme) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null, showProfileImage, theme);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId,
                                                       Integer keywordCoverage,
                                                       List<String> matchedKeywords,
                                                       List<String> missingKeywords,
                                                       boolean showProfileImage) {
        return buildApplicationDocument(userId, type, content, templateId, keywordCoverage, matchedKeywords,
                missingKeywords, showProfileImage, DocumentTheme.defaults());
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId,
                                                       Integer keywordCoverage,
                                                       List<String> matchedKeywords,
                                                       List<String> missingKeywords,
                                                       boolean showProfileImage,
                                                       DocumentTheme theme) {
        String resolvedTemplate = resolveTemplate(templateId, "application-modern");
        String exportMode = exportModeFromTemplate(resolvedTemplate);
        AtsReport atsReport = keywordCoverage != null && keywordCoverage > 0
                ? atsReportBuilder.forCoverage(keywordCoverage, matchedKeywords, missingKeywords, exportMode)
                : atsReportBuilder.basic(content, exportMode);
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        User user = userRepo.findById(userId).orElse(null);
        return new StructuredDocument(
                null,
                type,
                exportMode,
                resolvedTemplate,
                cvAssembler.buildIdentity(user, profile),
                new DocumentRenderOptions(showProfileImage, resolveTheme(theme)),
                List.of(),
                content != null ? content : "",
                atsReport);
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId) {
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage, templateId, false);
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, boolean showProfileImage) {
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage,
                templateId, showProfileImage, DocumentTheme.defaults());
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, boolean showProfileImage,
                                                 DocumentTheme theme) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        User user = userRepo.findById(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = resolveTemplate(templateId, "cv-ats-classic");
        String jobDescription = resolveJobDescription(jobId, rawJobDescription);
        TailoredCvContent tailored = tailoredCvGenerator.generate(source, jobDescription, customInstructions, targetLanguage);
        return cvAssembler.assemble(user, profile, source, tailored,
                exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, resolveTheme(theme));
    }

    /** @deprecated Use {@link #generateTailoredCv(UUID, UUID, String, String, String, String)} */
    @Deprecated
    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String exportMode, String templateId) {
        String resolvedTemplate = templateId != null && !templateId.isBlank()
                ? templateId : defaultCvTemplate(exportMode);
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage, resolvedTemplate, false);
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

    private static String resolveTemplate(String templateId, String defaultTemplate) {
        return templateId != null && !templateId.isBlank() ? templateId : defaultTemplate;
    }

    private static String defaultCvTemplate(String exportMode) {
        return "DESIGNED".equalsIgnoreCase(exportMode) ? "cv-modern-professional" : "cv-ats-classic";
    }

    private static String exportModeFromTemplate(String templateId) {
        if (templateId == null) return "ATS";
        String lower = templateId.toLowerCase();
        return lower.contains("ats") ? "ATS" : "DESIGNED";
    }

    private static DocumentTheme resolveTheme(DocumentTheme theme) {
        DocumentTheme defaults = DocumentTheme.defaults();
        if (theme == null) return defaults;
        return new DocumentTheme(
                CvDocumentAssembler.firstPresent(theme.primaryColor(), defaults.primaryColor()),
                CvDocumentAssembler.firstPresent(theme.accentColor(), defaults.accentColor()),
                CvDocumentAssembler.firstPresent(theme.fontFamily(), defaults.fontFamily()),
                CvDocumentAssembler.firstPresent(theme.fontScale(), defaults.fontScale()));
    }
}
