package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
import com.autoapplicant.domain.document.CompanyContext;
import com.autoapplicant.domain.document.PromptCategory;
import com.autoapplicant.domain.document.PromptTemplate;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.domain.job.JobContact;
import com.autoapplicant.domain.job.JobRequirement;
import com.autoapplicant.domain.job.RequirementKind;
import com.autoapplicant.domain.job.RequirementTier;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.document.PromptTemplateRepositoryPort;
import com.autoapplicant.port.out.document.WritingProfileRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.autoapplicant.usecase.document.PromptCompositionBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Belt-and-braces on the assembler: {@link PromptCompositionBuilderGoldenTest} pins what the builder
 * emits; this pins that the assembler actually threads the command's inputs into that builder and
 * carries the resolved context forward. Uses a real {@link PromptCompositionBuilder} (it is stateless)
 * behind mocked repositories, so a mis-wired argument shows up as a missing marker in the prompt.
 */
class ApplicationPromptAssemblerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PROFILE_JSON = "{\"summary\":\"engineer\"}";

    private final JobRepositoryPort jobRepo = mock(JobRepositoryPort.class);
    private final CareerProfileContextService careerProfileContext = mock(CareerProfileContextService.class);
    private final CompanyGroundingService companyGrounding = mock(CompanyGroundingService.class);
    private final PromptTemplateRepositoryPort promptTemplateRepo = mock(PromptTemplateRepositoryPort.class);
    private final WritingProfileRepositoryPort writingProfileRepo = mock(WritingProfileRepositoryPort.class);
    private final ApplicationRepositoryPort applicationRepo = mock(ApplicationRepositoryPort.class);

    private final ApplicationPromptAssembler assembler = new ApplicationPromptAssembler(
            jobRepo, careerProfileContext, companyGrounding, promptTemplateRepo,
            writingProfileRepo, applicationRepo, new PromptCompositionBuilder());

    @Test
    void assembleThreadsTheCommandIntoTheComposedPromptAndCarriesContextForward() {
        when(careerProfileContext.buildJson(any())).thenReturn(PROFILE_JSON);
        when(promptTemplateRepo.findDefaultFor(any(), any())).thenReturn(Optional.empty());
        when(writingProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(applicationRepo.findRecentOutcomeLessons(any(), anyInt())).thenReturn(List.of());

        GenerateDocumentCommand cmd = new GenerateDocumentCommand(
                USER, "COVER_LETTER", null, "MARKER_POSTING", null, null,
                "MARKER_CUSTOM", "MARKER_MOTIVATION", "English", false, null, "STANDARD");

        GenerationInputs inputs = assembler.assemble(cmd);

        // Context carried forward for the guard and scoring phases, without a job.
        assertThat(inputs.contactFreeJson()).isEqualTo(PROFILE_JSON);
        assertThat(inputs.jobDescription()).isEqualTo("MARKER_POSTING");
        assertThat(inputs.job()).isNull();

        // Each free-text input lands under its own heading: a swapped pair would still put both
        // markers in the prompt, but tell the model the candidate's motivation is an instruction.
        assertThat(inputs.composition().userPromptTemplate())
                .contains("## Job Description\nMARKER_POSTING")
                .contains("## Additional Instructions\nMARKER_CUSTOM")
                .contains("## Applicant's Personal Motivation\nMARKER_MOTIVATION");
    }

    @Test
    void groundsTheCompanyWithTheRequestingUsersOwnResearch() {
        UUID jobId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Job job = mock(Job.class);
        when(job.companyId()).thenReturn(companyId);
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(companyGrounding.contextFor(any(), any())).thenReturn(CompanyContext.EMPTY);
        when(careerProfileContext.buildJson(any())).thenReturn(PROFILE_JSON);
        when(promptTemplateRepo.findDefaultFor(any(), any())).thenReturn(Optional.empty());
        when(writingProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(applicationRepo.findRecentOutcomeLessons(any(), anyInt())).thenReturn(List.of());

        assembler.assemble(new GenerateDocumentCommand(
                USER, "COVER_LETTER", jobId, null, null, null,
                null, null, "English", false, null, "STANDARD"));

        verify(companyGrounding).contextFor(USER, companyId);
    }

    @Test
    void theCleanedPostingWinsOverThePastedOne() {
        stubDefaults();
        UUID jobId = givenJob(Job.builder().descriptionClean("CLEANED_POSTING").build());

        GenerationInputs inputs = assembler.assemble(command(jobId, "PASTED_POSTING", null));

        assertThat(inputs.jobDescription()).isEqualTo("CLEANED_POSTING");
        assertThat(inputs.composition().userPromptTemplate()).doesNotContain("PASTED_POSTING");
    }

    @Test
    void aJobWithoutACleanedPostingFallsBackToThePastedOne() {
        stubDefaults();
        UUID jobId = givenJob(Job.builder().build());

        GenerationInputs inputs = assembler.assemble(command(jobId, "PASTED_POSTING", null));

        assertThat(inputs.jobDescription()).isEqualTo("PASTED_POSTING");
    }

    @Test
    void theJobsCountryContactAndRequirementsReachThePrompt() {
        stubDefaults();
        UUID jobId = givenJob(Job.builder()
                .descriptionClean("We are hiring.")
                .country("Denmark")
                .contact(new JobContact("Mette Hansen", "afdelingsleder", null, null))
                .requirements(List.of(new JobRequirement("Mindst 5 års erfaring med backend",
                        RequirementTier.REQUIRED, RequirementKind.EXPERIENCE, null)))
                .build());

        String prompt = assembler.assemble(command(jobId, null, null))
                .composition().userPromptTemplate();

        // English language, Danish country: only the threaded country can select these rules.
        assertThat(prompt).contains("## Danish Market Conventions")
                .contains("The posting names Mette Hansen, afdelingsleder as the person to contact")
                .contains("Mindst 5 års erfaring med backend");
    }

    @Test
    void anExplicitTemplateIsUsedInsteadOfTheDefaultAndItsUsageCounted() {
        stubDefaults();
        PromptTemplate chosen = styleTemplate(USER, "CHOSEN_STYLE");
        when(promptTemplateRepo.findById(chosen.id())).thenReturn(Optional.of(chosen));

        String prompt = assembler.assemble(command(null, "posting", chosen.id()))
                .composition().userPromptTemplate();

        assertThat(prompt).contains("## Style Guidance\nCHOSEN_STYLE");
        verify(promptTemplateRepo, never()).findDefaultFor(any(), any());
        verify(promptTemplateRepo).incrementUsage(chosen.id());
    }

    @Test
    void withoutAnExplicitTemplateTheUsersDefaultIsUsedAndCounted() {
        stubDefaults();
        PromptTemplate fallback = styleTemplate(USER, "DEFAULT_STYLE");
        when(promptTemplateRepo.findDefaultFor(USER, "COVER_LETTER")).thenReturn(Optional.of(fallback));

        String prompt = assembler.assemble(command(null, "posting", null))
                .composition().userPromptTemplate();

        assertThat(prompt).contains("## Style Guidance\nDEFAULT_STYLE");
        verify(promptTemplateRepo).incrementUsage(fallback.id());
    }

    @Test
    void someoneElsesPrivateTemplateIsNeitherUsedNorCounted() {
        stubDefaults();
        PromptTemplate theirs = styleTemplate(UUID.randomUUID(), "THEIR_STYLE");
        when(promptTemplateRepo.findById(theirs.id())).thenReturn(Optional.of(theirs));

        String prompt = assembler.assemble(command(null, "posting", theirs.id()))
                .composition().userPromptTemplate();

        assertThat(prompt).doesNotContain("THEIR_STYLE");
        verify(promptTemplateRepo, never()).incrementUsage(any());
    }

    private void stubDefaults() {
        when(careerProfileContext.buildJson(any())).thenReturn(PROFILE_JSON);
        when(companyGrounding.contextFor(any(), any())).thenReturn(CompanyContext.EMPTY);
        when(promptTemplateRepo.findDefaultFor(any(), any())).thenReturn(Optional.empty());
        when(writingProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(applicationRepo.findRecentOutcomeLessons(any(), anyInt())).thenReturn(List.of());
    }

    private UUID givenJob(Job job) {
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        return jobId;
    }

    private static GenerateDocumentCommand command(UUID jobId, String pasted, UUID templateId) {
        return new GenerateDocumentCommand(USER, "COVER_LETTER", jobId, pasted, null, templateId,
                null, null, "English", false, null, "STANDARD");
    }

    private static PromptTemplate styleTemplate(UUID owner, String userPrompt) {
        return new PromptTemplate(UUID.randomUUID(), owner, "Style", PromptCategory.COVER_LETTER,
                null, null, userPrompt, null, false, null, 1, null, null, false, List.of(), 0,
                false, false, null);
    }
}
