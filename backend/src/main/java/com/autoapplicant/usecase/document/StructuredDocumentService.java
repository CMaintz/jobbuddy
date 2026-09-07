package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.document.BuildApplicationDocumentUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StructuredDocumentService implements GetCvRenderModelUseCase, GenerateTailoredCvUseCase,
        BuildApplicationDocumentUseCase, BuildApplicationDocumentPort {

    private static final String CV_TAILORING_CATEGORY = "CV_TAILORING";

    private final UserRepositoryPort userRepo;
    private final ProfileRepositoryPort profileRepo;
    private final ProfilePrivateInfoRepositoryPort privateInfoRepo;
    private final ProfileSocialRepositoryPort socialRepo;
    private final JobRepositoryPort jobRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final CareerProfileContextService careerProfileContext;
    private final CvDocumentAssembler cvAssembler;
    private final TailoredCvGenerator tailoredCvGenerator;
    private final AtsReportBuilder atsReportBuilder;

    public StructuredDocumentService(UserRepositoryPort userRepo,
                                     ProfileRepositoryPort profileRepo,
                                     ProfilePrivateInfoRepositoryPort privateInfoRepo,
                                     ProfileSocialRepositoryPort socialRepo,
                                     JobRepositoryPort jobRepo,
                                     PromptTemplateRepositoryPort promptTemplateRepo,
                                     CareerProfileContextService careerProfileContext,
                                     CvDocumentAssembler cvAssembler,
                                     TailoredCvGenerator tailoredCvGenerator,
                                     AtsReportBuilder atsReportBuilder) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
        this.jobRepo = jobRepo;
        this.promptTemplateRepo = promptTemplateRepo;
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

    @Override
    public StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage, DocumentTheme theme) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        ProfilePrivateInfo privateInfo = privateInfoRepo.findByUserId(userId).orElse(null);
        List<ProfileSocial> socials = socialRepo.findByUserId(userId);
        User user = userRepo.findById(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = resolveTemplate(templateId, "cv-ats-classic");
        return cvAssembler.assemble(user, profile, privateInfo, socials, source, null,
                exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, resolveTheme(theme));
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null, false);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId, boolean showProfileImage) {
        return buildApplicationDocument(userId, type, content, templateId, null, null, null, showProfileImage);
    }

    @Override
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

    @Override
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
        ProfilePrivateInfo privateInfo = privateInfoRepo.findByUserId(userId).orElse(null);
        List<ProfileSocial> socials = socialRepo.findByUserId(userId);
        User user = userRepo.findById(userId).orElse(null);
        return new StructuredDocument(
                null,
                type,
                exportMode,
                resolvedTemplate,
                cvAssembler.buildIdentity(user, profile, privateInfo, socials),
                new DocumentRenderOptions(showProfileImage, resolveTheme(theme)),
                List.of(),
                content != null ? content : "",
                atsReport);
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId) {
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage,
                templateId, null, false, DocumentTheme.defaults());
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, boolean showProfileImage) {
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage,
                templateId, null, showProfileImage, DocumentTheme.defaults());
    }

    @Override
    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, UUID promptTemplateId,
                                                 boolean showProfileImage, DocumentTheme theme) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        ProfilePrivateInfo privateInfo = privateInfoRepo.findByUserId(userId).orElse(null);
        List<ProfileSocial> socials = socialRepo.findByUserId(userId);
        User user = userRepo.findById(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = resolveTemplate(templateId, "cv-ats-classic");
        String jobDescription = resolveJobDescription(jobId, rawJobDescription);
        PromptTemplate promptTemplate = resolvePromptTemplate(promptTemplateId, CV_TAILORING_CATEGORY);
        TailoredCvContent tailored = tailoredCvGenerator.generate(
                source, jobDescription, customInstructions, targetLanguage, promptTemplate);
        return cvAssembler.assemble(user, profile, privateInfo, socials, source, tailored,
                exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, resolveTheme(theme));
    }

    /** Loads the prompt template by explicit ID, or falls back to the system default for the category. */
    private PromptTemplate resolvePromptTemplate(UUID promptTemplateId, String fallbackCategory) {
        if (promptTemplateId != null) {
            return promptTemplateRepo.findById(promptTemplateId).orElse(null);
        }
        return promptTemplateRepo.findSystemDefault(fallbackCategory).orElse(null);
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
