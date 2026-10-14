package com.autoapplicant.usecase.document;

import com.autoapplicant.domain.document.DocumentType;
import com.autoapplicant.domain.document.structured.AtsReport;
import com.autoapplicant.domain.document.structured.ContentGuardFindings;
import com.autoapplicant.domain.document.structured.DocumentRenderOptions;
import com.autoapplicant.domain.document.structured.JobKeywords;
import com.autoapplicant.domain.document.structured.KeywordCoverage;
import com.autoapplicant.domain.document.structured.StructuredDocument;
import com.autoapplicant.domain.job.JobRequirement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Wraps a letter or application body in a {@link StructuredDocument}: the user's identity, the
 * render options and an ATS report measured against the body itself. Shared by the export
 * endpoint and the generation pipeline, so it is a helper rather than a use case of its own.
 */
@Component
public class ApplicationDocumentAssembler {

    private static final String DEFAULT_TEMPLATE = "application-modern";

    private final DocumentOwnerLoader owners;
    private final CvDocumentAssembler cvAssembler;
    private final AtsReportBuilder atsReportBuilder;
    private final KeywordCoverageCalculator coverageCalculator;

    public ApplicationDocumentAssembler(DocumentOwnerLoader owners,
                                        CvDocumentAssembler cvAssembler,
                                        AtsReportBuilder atsReportBuilder,
                                        KeywordCoverageCalculator coverageCalculator) {
        this.owners = owners;
        this.cvAssembler = cvAssembler;
        this.atsReportBuilder = atsReportBuilder;
        this.coverageCalculator = coverageCalculator;
    }

    public StructuredDocument assemble(UUID userId, DocumentType type, String content, String templateId,
                                       JobKeywords jobKeywords, DocumentRenderOptions render,
                                       ContentGuardFindings guardFindings) {
        String resolvedTemplate = DocumentTemplates.resolveTemplate(templateId, DEFAULT_TEMPLATE);
        String exportMode = DocumentTemplates.exportModeFromTemplate(resolvedTemplate);
        ContentGuardFindings findings = guardFindings != null ? guardFindings : ContentGuardFindings.NONE;
        DocumentOwnerLoader.DocumentOwner owner = owners.load(userId);
        return new StructuredDocument(
                null,
                type,
                exportMode,
                resolvedTemplate,
                cvAssembler.buildIdentity(owner.user(), owner.profile(), owner.privateInfo(), owner.socials()),
                new DocumentRenderOptions(render.showProfileImage(), DocumentTemplates.resolveTheme(render.theme())),
                List.of(),
                content != null ? content : "",
                atsReport(content, jobKeywords, exportMode, findings));
    }

    private AtsReport atsReport(String content, JobKeywords jobKeywords, String exportMode,
                                ContentGuardFindings findings) {
        // The letter knows its own language: detect it from the delivered body rather than
        // threading yet another parameter through every caller.
        String documentLanguage = JobLanguageDetector.detect(content);
        // Measured here against the letter's own text; the model is not asked how well it did.
        KeywordCoverage coverage = coverageCalculator.measure(content, jobKeywords);
        List<JobRequirement> unmeasurable =
                jobKeywords != null ? jobKeywords.unmeasurableRequirements() : List.of();
        return coverage.measured()
                ? atsReportBuilder.forCoverage(coverage, exportMode, findings, documentLanguage, unmeasurable)
                : atsReportBuilder.basic(content, exportMode, findings, documentLanguage, unmeasurable);
    }
}
