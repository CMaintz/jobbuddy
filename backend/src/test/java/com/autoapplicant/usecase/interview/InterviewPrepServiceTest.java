package com.autoapplicant.usecase.interview;

import com.autoapplicant.domain.document.PromptComposition;
import com.autoapplicant.domain.interview.InterviewQuestion;
import com.autoapplicant.domain.interview.MockInterviewTurn;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.ai.ChatProviderPort;
import com.autoapplicant.port.out.document.GeneratedDocumentRepositoryPort;
import com.autoapplicant.port.out.interview.InterviewQuestionRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.port.out.user.InterviewStoryRepositoryPort;
import com.autoapplicant.usecase.document.CareerProfileContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * There is one set of interview questions per job, and the mock interview asks that set.
 * Before this, question generation and the prep pack were separate generators, so the candidate
 * could be shown two different "your questions" lists for the same job — and then practise
 * against a mock interviewer that had invented a third.
 */
@ExtendWith(MockitoExtension.class)
class InterviewPrepServiceTest {

    @Mock InterviewQuestionRepositoryPort repo;
    @Mock ChatProviderPort aiProvider;
    @Mock JobRepositoryPort jobRepo;
    @Mock GeneratedDocumentRepositoryPort documentRepo;
    @Mock CareerProfileContextService careerProfileContext;
    @Mock InterviewStoryRepositoryPort storyRepo;

    InterviewPrepService service;
    final UUID userId = UUID.randomUUID();
    final UUID jobId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InterviewPrepService(repo, aiProvider, new ObjectMapper(), jobRepo,
                documentRepo, careerProfileContext, storyRepo);
    }

    @Test
    void generating_questions_for_a_known_job_goes_through_the_prep_pack() {
        stubPrepPackJob();
        when(aiProvider.generateJson(any(), any())).thenReturn("""
                {"questions":[{"question":"Walk me through the Kafka migration.","category":"TECHNICAL"}],
                 "consistencyBrief":["You claimed a 40% latency drop."],
                 "questionsToAsk":["How is the team structured?"]}""");

        List<InterviewQuestion> questions = service.generateQuestions(jobId, userId, "ignored", 9);

        assertThat(questions).extracting(InterviewQuestion::question)
                .containsExactly("Walk me through the Kafka migration.");

        // The prep-pack prompt is the one that reaches the provider — it carries the candidate's
        // profile and asks for the consistency brief; the bare job-description prompt does not.
        PromptComposition sent = captureComposition();
        assertThat(sent.resolvedFinalPrompt()).contains("consistencyBrief");
        assertThat(sent.resolvedFinalPrompt()).contains("Candidate profile");
    }

    @Test
    void the_requested_question_count_reaches_the_prep_pack_prompt() {
        stubPrepPackJob();
        when(aiProvider.generateJson(any(), any())).thenReturn("{\"questions\":[]}");

        service.generateQuestions(jobId, userId, "ignored", 4);

        assertThat(captureComposition().resolvedFinalPrompt()).contains("Give 4 questions");
    }

    @Test
    void a_question_set_with_no_job_behind_it_still_uses_the_standalone_path() {
        // An unsolicited approach or a hand-pasted role: there is no posting to load and no
        // submitted documents to stay consistent with, so the prep pack has nothing to work from.
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());
        when(aiProvider.generateJson(any(), any())).thenReturn(
                "[{\"question\":\"Why this field?\",\"category\":\"BEHAVIORAL\"}]");
        when(repo.findByJobIdAndUserId(jobId, userId)).thenReturn(List.of());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<InterviewQuestion> questions = service.generateQuestions(jobId, userId, "A job", 3);

        assertThat(questions).extracting(InterviewQuestion::question).containsExactly("Why this field?");
        assertThat(captureComposition().resolvedFinalPrompt()).contains("Generate 3 interview questions");
    }

    @Test
    void the_mock_interviewer_is_handed_the_prepared_questions_as_its_plan() {
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job()));
        when(careerProfileContext.buildJson(userId)).thenReturn("{}");
        when(repo.findByJobIdAndUserId(jobId, userId)).thenReturn(List.of(
                question("Walk me through the Kafka migration.")));
        when(aiProvider.generate(any(), any())).thenReturn("So, tell me about yourself.");

        service.respond(userId, jobId, List.of(), false);

        ArgumentCaptor<PromptComposition> captor = ArgumentCaptor.forClass(PromptComposition.class);
        verify(aiProvider).generate(captor.capture(), any());
        assertThat(captor.getValue().resolvedFinalPrompt())
                .contains("Walk me through the Kafka migration.")
                .contains("question plan");
    }

    @Test
    void the_mock_interviewer_works_before_any_questions_have_been_prepared() {
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job()));
        when(careerProfileContext.buildJson(userId)).thenReturn("{}");
        when(repo.findByJobIdAndUserId(jobId, userId)).thenReturn(List.of());
        when(aiProvider.generate(any(), any())).thenReturn("Tell me about yourself.");

        String reply = service.respond(userId, jobId,
                List.of(new MockInterviewTurn("candidate", "Hi")), false);

        assertThat(reply).isEqualTo("Tell me about yourself.");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubPrepPackJob() {
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job()));
        when(documentRepo.findByJobId(jobId)).thenReturn(List.of());
        when(careerProfileContext.buildJson(userId)).thenReturn("{\"skills\":[\"Java\"]}");
        when(storyRepo.findByUserId(userId)).thenReturn(List.of());
        when(repo.findByJobIdAndUserId(jobId, userId)).thenReturn(List.of());
        // Not reached when the model returns no questions — one of these tests checks exactly that.
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PromptComposition captureComposition() {
        ArgumentCaptor<PromptComposition> captor = ArgumentCaptor.forClass(PromptComposition.class);
        verify(aiProvider).generateJson(captor.capture(), any());
        return captor.getValue();
    }

    private Job job() {
        return Job.builder().id(jobId).title("Backend Engineer").companyName("Acme")
                .descriptionClean("We need Java and Kafka.").country("DK").build();
    }

    private InterviewQuestion question(String text) {
        return new InterviewQuestion(UUID.randomUUID(), jobId, userId, text,
                "TECHNICAL", null, false, 0, null, null);
    }
}
