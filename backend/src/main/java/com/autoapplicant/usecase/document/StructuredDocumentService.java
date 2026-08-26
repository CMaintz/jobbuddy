package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.user.Profile;
import com.autoapplicant.domain.user.ProfilePrivateInfo;
import com.autoapplicant.domain.user.ProfileSocial;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.port.in.document.BuildApplicationDocumentUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.BuildApplicationDocumentPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.ProfilePrivateInfoRepositoryPort;
import com.autoapplicant.port.out.user.ProfileRepositoryPort;
import com.autoapplicant.port.out.user.ProfileSocialRepositoryPort;
import com.autoapplicant.port.out.user.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
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
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final CareerProfileContextService careerProfileContext;
    private final CvDocumentAssembler cvAssembler;
    private final TailoredCvGenerator tailoredCvGenerator;
    private final TailoredCvReviewer tailoredCvReviewer;
    private final AtsReportBuilder atsReportBuilder;
    private final ApplicationRepositoryPort applicationRepo;
    private final GeneratedContentGuards contentGuards;

    public StructuredDocumentService(UserRepositoryPort userRepo,
                                     ProfileRepositoryPort profileRepo,
                                     ProfilePrivateInfoRepositoryPort privateInfoRepo,
                                     ProfileSocialRepositoryPort socialRepo,
                                     JobRepositoryPort jobRepo,
                                     PromptTemplateRepositoryPort promptTemplateRepo,
                                     WritingProfileRepositoryPort writingProfileRepo,
                                     CareerProfileContextService careerProfileContext,
                                     CvDocumentAssembler cvAssembler,
                                     TailoredCvGenerator tailoredCvGenerator,
                                     TailoredCvReviewer tailoredCvReviewer,
                                     AtsReportBuilder atsReportBuilder,
                                     ApplicationRepositoryPort applicationRepo,
                                     GeneratedContentGuards contentGuards) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.privateInfoRepo = privateInfoRepo;
        this.socialRepo = socialRepo;
        this.jobRepo = jobRepo;
        this.promptTemplateRepo = promptTemplateRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.careerProfileContext = careerProfileContext;
        this.cvAssembler = cvAssembler;
        this.tailoredCvGenerator = tailoredCvGenerator;
        this.tailoredCvReviewer = tailoredCvReviewer;
        this.atsReportBuilder = atsReportBuilder;
        this.applicationRepo = applicationRepo;
        this.contentGuards = contentGuards;
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
                showProfileImage, resolveTheme(theme), ContentGuardFindings.NONE, null);
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
        return buildApplicationDocument(userId, type, content, templateId, null, null, null,
                showProfileImage, theme, ContentGuardFindings.NONE);
    }

    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId,
                                                       Integer keywordCoverage,
                                                       List<String> matchedKeywords,
                                                       List<String> missingKeywords,
                                                       boolean showProfileImage) {
        return buildApplicationDocument(userId, type, content, templateId, keywordCoverage, matchedKeywords,
                missingKeywords, showProfileImage, DocumentTheme.defaults(), ContentGuardFindings.NONE);
    }

    @Override
    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId,
                                                       Integer keywordCoverage,
                                                       List<String> matchedKeywords,
                                                       List<String> missingKeywords,
                                                       boolean showProfileImage,
                                                       DocumentTheme theme,
                                                       ContentGuardFindings guardFindings) {
        String resolvedTemplate = resolveTemplate(templateId, "application-modern");
        String exportMode = exportModeFromTemplate(resolvedTemplate);
        ContentGuardFindings findings = guardFindings != null ? guardFindings : ContentGuardFindings.NONE;
        // The letter knows its own language: detect it from the delivered body rather than
        // threading yet another parameter through four overloads and the port.
        String documentLanguage = JobLanguageDetector.detect(content);
        AtsReport atsReport = keywordCoverage != null && keywordCoverage > 0
                ? atsReportBuilder.forCoverage(keywordCoverage, matchedKeywords, missingKeywords,
                        exportMode, findings, documentLanguage)
                : atsReportBuilder.basic(content, exportMode, findings, documentLanguage);
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
                templateId, null, false, DocumentTheme.defaults(), null);
    }

    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, boolean showProfileImage) {
        return generateTailoredCv(userId, jobId, rawJobDescription, customInstructions, targetLanguage,
                templateId, null, showProfileImage, DocumentTheme.defaults(), null);
    }

    @Override
    public StructuredDocument generateTailoredCv(UUID userId, UUID jobId, String rawJobDescription,
                                                 String customInstructions, String targetLanguage,
                                                 String templateId, UUID promptTemplateId,
                                                 boolean showProfileImage, DocumentTheme theme,
                                                 String lengthPreference) {
        Profile profile = profileRepo.findByUserId(userId).orElse(null);
        ProfilePrivateInfo privateInfo = privateInfoRepo.findByUserId(userId).orElse(null);
        List<ProfileSocial> socials = socialRepo.findByUserId(userId);
        User user = userRepo.findById(userId).orElse(null);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = resolveTemplate(templateId, "cv-ats-classic");
        // One lookup: the posting supplies both the description and the country whose hiring
        // conventions the prompts should follow.
        Job job = jobId != null ? jobRepo.findById(jobId).orElse(null) : null;
        String jobDescription = job != null ? job.descriptionClean() : rawJobDescription;
        PostingContext posting = new PostingContext(jobDescription,
                job != null ? job.country() : null, null);
        PromptTemplate promptTemplate = resolvePromptTemplate(promptTemplateId, CV_TAILORING_CATEGORY);
        WritingProfile writingProfile = writingProfileRepo.findByUserId(userId).orElse(null);
        TailoredCvContent tailored = tailoredCvGenerator.generate(
                source, posting, customInstructions, targetLanguage, promptTemplate,
                writingProfile, applicationRepo.findRecentOutcomeLessons(userId, 5), lengthPreference);
        // Drafter→reviewer pass on the structured CV (config-gated); non-fatal on failure.
        tailored = tailoredCvReviewer.review(tailored, posting, writingProfile, targetLanguage);
        // Same deterministic backstops as cover letters: fact gate + retracted claims on the CV text.
        ContentGuardFindings findings = contentGuards.verify(
                userId, cvText(tailored), careerProfileContext.buildJson(userId), "CV");
        return cvAssembler.assemble(user, profile, privateInfo, socials, source, tailored,
                exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, resolveTheme(theme), findings,
                JobLanguageDetector.resolve(targetLanguage, jobDescription));
    }

    /** Flattens the tailored CV's rewritten text so the guards can check it like a letter body. */
    private static String cvText(TailoredCvContent t) {
        StringBuilder sb = new StringBuilder();
        if (t.selectedProfile() != null) sb.append(t.selectedProfile()).append('\n');
        if (t.selectedSkills() != null) sb.append(String.join(", ", t.selectedSkills())).append('\n');
        appendItems(sb, t.experience());
        appendItems(sb, t.projects());
        appendItems(sb, t.education());
        appendItems(sb, t.certifications());
        return sb.toString();
    }

    private static void appendItems(StringBuilder sb,
                                    List<com.autoapplicant.domain.document.structured.StructuredDocumentItem> items) {
        if (items == null) return;
        for (var it : items) {
            if (it.title() != null) sb.append(it.title()).append(' ');
            if (it.subtitle() != null) sb.append(it.subtitle()).append(' ');
            if (it.description() != null) sb.append(it.description()).append('\n');
            if (it.bullets() != null) for (String b : it.bullets()) sb.append(b).append('\n');
        }
    }

    /** Loads the prompt template by explicit ID, or falls back to the system default for the category. */
    private PromptTemplate resolvePromptTemplate(UUID promptTemplateId, String fallbackCategory) {
        PromptTemplate resolved = promptTemplateId != null
                ? promptTemplateRepo.findById(promptTemplateId).orElse(null)
                : promptTemplateRepo.findSystemDefault(fallbackCategory).orElse(null);
        if (resolved != null) promptTemplateRepo.incrementUsage(resolved.id());
        return resolved;
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
