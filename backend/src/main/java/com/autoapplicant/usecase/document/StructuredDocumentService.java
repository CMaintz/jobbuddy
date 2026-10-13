package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.CvSectionPrompts;
import com.autoapplicant.domain.document.CvTailoringGuidance;
import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.document.WritingProfile;
import com.autoapplicant.domain.document.structured.*;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.document.BuildApplicationDocumentUseCase;
import com.autoapplicant.port.in.document.GenerateTailoredCvUseCase;
import com.autoapplicant.port.in.document.GetCvRenderModelUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StructuredDocumentService implements GetCvRenderModelUseCase, GenerateTailoredCvUseCase,
        BuildApplicationDocumentUseCase {

    private static final String CV_TAILORING_CATEGORY = "CV_TAILORING";

    private final DocumentOwnerLoader owners;
    private final JobRepositoryPort jobRepo;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final CareerProfileContextService careerProfileContext;
    private final CvDocumentAssembler cvAssembler;
    private final TailoredCvGenerator tailoredCvGenerator;
    private final TailoredCvReviewer tailoredCvReviewer;
    private final ApplicationDocumentAssembler applicationDocuments;
    private final ApplicationRepositoryPort applicationRepo;
    private final GeneratedContentGuards contentGuards;
    private final CvSectionTemplates sectionTemplates;

    public StructuredDocumentService(DocumentOwnerLoader owners,
                                     JobRepositoryPort jobRepo,
                                     PromptTemplateRepositoryPort promptTemplateRepo,
                                     WritingProfileRepositoryPort writingProfileRepo,
                                     CareerProfileContextService careerProfileContext,
                                     CvDocumentAssembler cvAssembler,
                                     TailoredCvGenerator tailoredCvGenerator,
                                     TailoredCvReviewer tailoredCvReviewer,
                                     ApplicationDocumentAssembler applicationDocuments,
                                     ApplicationRepositoryPort applicationRepo,
                                     GeneratedContentGuards contentGuards,
                                     CvSectionTemplates sectionTemplates) {
        this.owners = owners;
        this.jobRepo = jobRepo;
        this.promptTemplateRepo = promptTemplateRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.careerProfileContext = careerProfileContext;
        this.cvAssembler = cvAssembler;
        this.tailoredCvGenerator = tailoredCvGenerator;
        this.tailoredCvReviewer = tailoredCvReviewer;
        this.applicationDocuments = applicationDocuments;
        this.applicationRepo = applicationRepo;
        this.contentGuards = contentGuards;
        this.sectionTemplates = sectionTemplates;
    }

    public StructuredDocument buildCv(UUID userId, String templateId) {
        return buildCv(userId, templateId, false);
    }

    public StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage) {
        return buildCv(userId, templateId, showProfileImage, DocumentTheme.defaults());
    }

    @Override
    public StructuredDocument buildCv(UUID userId, String templateId, boolean showProfileImage, DocumentTheme theme) {
        DocumentOwnerLoader.DocumentOwner owner = owners.load(userId);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = DocumentTemplates.resolveTemplate(templateId, "cv-ats-classic");
        return cvAssembler.assemble(owner.user(), owner.profile(), owner.privateInfo(),
                owner.socials(), source, null,
                DocumentTemplates.exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, DocumentTemplates.resolveTheme(theme), ContentGuardFindings.NONE, null,
                JobKeywords.NONE, null);
    }

    @Override
    public StructuredDocument buildApplicationDocument(UUID userId, DocumentType type, String content,
                                                       String templateId, boolean showProfileImage,
                                                       DocumentTheme theme) {
        return applicationDocuments.assemble(userId, type, content, templateId, JobKeywords.NONE,
                new DocumentRenderOptions(showProfileImage, theme), ContentGuardFindings.NONE);
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
        DocumentOwnerLoader.DocumentOwner owner = owners.load(userId);
        CareerProfileForAi source = careerProfileContext.build(userId);
        String resolvedTemplate = DocumentTemplates.resolveTemplate(templateId, "cv-ats-classic");
        // One lookup: the posting supplies both the description and the country whose hiring
        // conventions the prompts should follow.
        Job job = jobId != null ? jobRepo.findById(jobId).orElse(null) : null;
        String jobDescription = job != null ? job.descriptionClean() : rawJobDescription;
        PostingContext posting = new PostingContext(jobDescription,
                job != null ? job.country() : null, null,
                job != null ? job.requirements() : java.util.List.of());
        PromptTemplate promptTemplate = resolvePromptTemplate(userId, promptTemplateId, CV_TAILORING_CATEGORY);
        WritingProfile writingProfile = writingProfileRepo.findByUserId(userId).orElse(null);
        // The user's analysed style + their chosen section templates, travelling together
        // so drafting and the review pass apply the same guidance.
        CvSectionPrompts sectionPrompts = sectionTemplates.defaultsFor(userId);
        CvTailoringGuidance guidance = new CvTailoringGuidance(writingProfile, sectionPrompts);
        TailoredCvContent tailored = tailoredCvGenerator.generate(
                source, posting, customInstructions, targetLanguage, promptTemplate,
                guidance, applicationRepo.findRecentOutcomeLessons(userId, 5), lengthPreference);
        // Drafter→reviewer pass on the structured CV (config-gated); non-fatal on failure.
        tailored = tailoredCvReviewer.review(tailored, posting, guidance, targetLanguage);
        // Same deterministic backstops as cover letters: fact gate + retracted claims on the CV text.
        ContentGuardFindings findings = contentGuards.verify(
                userId, cvText(tailored), careerProfileContext.buildJson(userId), "CV");
        String cvText = cvText(tailored);
        return cvAssembler.assemble(owner.user(), owner.profile(), owner.privateInfo(),
                owner.socials(), source, tailored,
                DocumentTemplates.exportModeFromTemplate(resolvedTemplate), resolvedTemplate,
                showProfileImage, DocumentTemplates.resolveTheme(theme), findings,
                JobLanguageDetector.resolve(targetLanguage, jobDescription),
                JobKeywords.of(job), cvText);
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
        // Custom-section text must be flattened too, or the fact/retracted guards never see it.
        if (t.customSections() != null) {
            for (StructuredDocumentSection cs : t.customSections()) {
                if (cs.heading() != null) sb.append(cs.heading()).append('\n');
                appendItems(sb, cs.items());
            }
        }
        return sb.toString();
    }

    private static void appendItems(StringBuilder sb,
                                    List<com.autoapplicant.domain.document.structured.StructuredDocumentItem> items) {
        if (items == null) return;
        for (StructuredDocumentItem it : items) {
            if (it.title() != null) sb.append(it.title()).append(' ');
            if (it.subtitle() != null) sb.append(it.subtitle()).append(' ');
            if (it.description() != null) sb.append(it.description()).append('\n');
            if (it.bullets() != null) for (String b : it.bullets()) sb.append(b).append('\n');
        }
    }

    /**
     * Loads the prompt template by explicit ID, or falls back to the caller's default for the
     * category — their own chosen one when they have set it, the app's seeded prompt otherwise.
     */
    private PromptTemplate resolvePromptTemplate(UUID userId, UUID promptTemplateId, String fallbackCategory) {
        PromptTemplate resolved = promptTemplateId != null
                ? promptTemplateRepo.findById(promptTemplateId).filter(t -> t.isVisibleTo(userId)).orElse(null)
                : promptTemplateRepo.findDefaultFor(userId, fallbackCategory).orElse(null);
        if (resolved != null) promptTemplateRepo.incrementUsage(resolved.id());
        return resolved;
    }
}
