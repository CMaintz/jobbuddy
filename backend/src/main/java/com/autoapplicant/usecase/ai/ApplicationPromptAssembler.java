package com.autoapplicant.usecase.ai;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.CompanyContext;
import com.autoapplicant.domain.document.PostingContext;
import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Resolves everything the generation prompt needs — the posting, grounded company facts, the style
 * template, the writing profile, past-outcome lessons — and composes the prompt from them. The one
 * place that reads the request's context so the downstream phases receive it in a {@link GenerationInputs}
 * carrier rather than re-fetching it.
 */
@Service
public class ApplicationPromptAssembler {

    private final JobRepositoryPort jobRepo;
    private final CareerProfileContextService careerProfileContext;
    private final CompanyGroundingService companyGrounding;
    private final PromptTemplateRepositoryPort promptTemplateRepo;
    private final WritingProfileRepositoryPort writingProfileRepo;
    private final ApplicationRepositoryPort applicationRepo;
    private final PromptCompositionBuilder compositionBuilder;

    public ApplicationPromptAssembler(JobRepositoryPort jobRepo,
                                      CareerProfileContextService careerProfileContext,
                                      CompanyGroundingService companyGrounding,
                                      PromptTemplateRepositoryPort promptTemplateRepo,
                                      WritingProfileRepositoryPort writingProfileRepo,
                                      ApplicationRepositoryPort applicationRepo,
                                      PromptCompositionBuilder compositionBuilder) {
        this.jobRepo = jobRepo;
        this.careerProfileContext = careerProfileContext;
        this.companyGrounding = companyGrounding;
        this.promptTemplateRepo = promptTemplateRepo;
        this.writingProfileRepo = writingProfileRepo;
        this.applicationRepo = applicationRepo;
        this.compositionBuilder = compositionBuilder;
    }

    public GenerationInputs assemble(GenerateDocumentCommand cmd) {
        Job job = cmd.jobId() != null ? jobRepo.findById(cmd.jobId()).orElse(null) : null;
        String jobDescription = job != null && job.descriptionClean() != null
                ? job.descriptionClean() : cmd.rawJobDescription();
        String contactFreeJson = careerProfileContext.buildJson(cmd.userId());
        // Company grounding (verified facts from the company's own site + the candidate's own
        // research) so company references in cover letters are accurate rather than parroted from
        // the untrusted posting.
        CompanyContext companyContext = job != null
                ? companyGrounding.contextFor(job.companyId()) : CompanyContext.EMPTY;

        PromptComposition composition = compositionBuilder.composeStructuredApplicationPrompt(
                cmd.documentType(), contactFreeJson, postingFor(job, jobDescription),
                cmd.customInstructions(), cmd.motivationText(), cmd.targetLanguage(), resolveStyleTemplate(cmd),
                writingProfileRepo.findByUserId(cmd.userId()).orElse(null),
                applicationRepo.findRecentOutcomeLessons(cmd.userId(), 5),
                companyContext, cmd.lengthPreference());
        return new GenerationInputs(composition, job, contactFreeJson, jobDescription);
    }

    /** The user's chosen template, else their default; usage is counted on the one that wins. */
    private PromptTemplate resolveStyleTemplate(GenerateDocumentCommand cmd) {
        PromptTemplate styleTemplate = cmd.promptTemplateId() != null
                ? promptTemplateRepo.findById(cmd.promptTemplateId()).orElse(null)
                // The user's own chosen default when they have one, the app's seeded prompt
                // otherwise — switching a default is their row, never a write to app content.
                : promptTemplateRepo.findDefaultFor(cmd.userId(), cmd.documentType()).orElse(null);
        if (styleTemplate != null) {
            promptTemplateRepo.incrementUsage(styleTemplate.id());
        }
        return styleTemplate;
    }

    private static PostingContext postingFor(Job job, String jobDescription) {
        return new PostingContext(jobDescription,
                job != null ? job.country() : null,
                job != null && job.contact() != null && job.contact().hasName()
                        ? job.contact().display() : null,
                job != null ? job.requirements() : List.of());
    }
}
