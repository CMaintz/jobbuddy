package com.autoapplicant.usecase.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.ai.GenerateDocumentCommand;
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

        // The command's free-text inputs actually reached the composed prompt.
        assertThat(inputs.composition().userPromptTemplate())
                .contains("MARKER_POSTING")
                .contains("MARKER_CUSTOM")
                .contains("MARKER_MOTIVATION");
    }

    @Test
    void withNoJobItNeitherLooksUpNorGroundsACompany() {
        when(careerProfileContext.buildJson(any())).thenReturn(PROFILE_JSON);
        when(promptTemplateRepo.findDefaultFor(any(), any())).thenReturn(Optional.empty());
        when(writingProfileRepo.findByUserId(any())).thenReturn(Optional.empty());
        when(applicationRepo.findRecentOutcomeLessons(any(), anyInt())).thenReturn(List.of());

        assembler.assemble(new GenerateDocumentCommand(
                USER, "COVER_LETTER", null, "a pasted posting", null, null,
                null, null, "English", false, null, "STANDARD"));

        verify(jobRepo, never()).findById(any());
        verify(companyGrounding, never()).factsFor(any());
    }
}
