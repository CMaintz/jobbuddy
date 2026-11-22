package com.autoapplicant.adapter.chatplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.application.Application;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.PlanItem.State;
import com.autoapplicant.domain.chat.ProposalRefusedException;
import com.autoapplicant.domain.chat.ProposedChange;
import com.autoapplicant.domain.common.NotFoundException;
import com.autoapplicant.domain.document.GenerationSettings;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.in.application.GetApplicationByIdUseCase;
import com.autoapplicant.port.in.application.UpdateApplicationDatesUseCase;
import com.autoapplicant.port.in.application.UpdateApplicationStatusUseCase;
import com.autoapplicant.port.in.job.IgnoreJobUseCase;
import com.autoapplicant.port.in.job.SaveJobUseCase;
import com.autoapplicant.port.out.application.ApplicationRepositoryPort;
import com.autoapplicant.port.out.chat.ChatRepositoryPort;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import com.autoapplicant.usecase.chat.ChatPlanService;
import com.autoapplicant.usecase.chat.InMemoryChatPlans;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Her click is the only thing that changes her data: a proposal changes nothing, a rejection
 * changes nothing, and an approval runs once, as her, against her data as it is at that moment.
 */
class ChatPlanApprovalTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 1);
    private static final String POSTING_TEXT =
            "Vi søger en rådgiver, der kan skrive klart til borgerne og planlægge kampagner.";
    private static final LocalDateTime INTERVIEW = LocalDateTime.of(2026, 12, 3, 10, 0);

    private final InMemoryChatPlans plans = new InMemoryChatPlans();
    private final ChatRepositoryPort chats = mock(ChatRepositoryPort.class);
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);
    private final ApplicationRepositoryPort applications = mock(ApplicationRepositoryPort.class);
    private final ChatPlanService service = new ChatPlanService(plans, chats, jobs, applications,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final SaveJobUseCase saveJob = mock(SaveJobUseCase.class);
    private final IgnoreJobUseCase ignoreJob = mock(IgnoreJobUseCase.class);
    private final UpdateApplicationStatusUseCase updateStatus = mock(UpdateApplicationStatusUseCase.class);
    private final UpdateApplicationDatesUseCase updateDates = mock(UpdateApplicationDatesUseCase.class);
    private final GetApplicationByIdUseCase getApplication = mock(GetApplicationByIdUseCase.class);
    private final DocumentGeneration generation = mock(DocumentGeneration.class);
    private final ApplicationForPosting applicationFor = mock(ApplicationForPosting.class);
    /** Holds queued document runs until a test lets them go, as the background thread would. */
    private final List<Runnable> queued = new ArrayList<>();
    private final ChatPlanApproval approval = new ChatPlanApproval(service, new PlanItemRunner(
            saveJob, ignoreJob, updateStatus, updateDates, getApplication),
            new DocumentQueue(service, generation, applicationFor, queued::add));

    private final UUID her = UUID.randomUUID();
    private final UUID someoneElse = UUID.randomUUID();
    private final UUID chatId = UUID.randomUUID();
    private final UUID postingId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @BeforeEach
    void givenHerChatAPostingAndAnApplication() {
        when(chats.findById(chatId)).thenReturn(Optional.of(
                new Chat(chatId, her, UUID.randomUUID(), null, NOW, NOW)));
        when(jobs.findById(postingId)).thenReturn(Optional.of(
                Job.builder().id(postingId).title("Rådgiver").companyName("Kommunen")
                        .descriptionClean(POSTING_TEXT).build()));
        givenHerApplicationIs(ApplicationStatus.APPLIED, null);
    }

    @Test
    void a_proposal_changes_nothing_until_she_approves_it() {
        ChatPlan plan = proposeSave();

        assertThat(plan.items()).singleElement().satisfies(i -> {
            assertThat(i.state()).isEqualTo(State.PENDING);
            assertThat(i.details().title()).isEqualTo("Rådgiver");
        });
        verifyNoInteractions(saveJob, ignoreJob, updateStatus, updateDates);
    }

    @Test
    void approving_runs_the_change_as_her_and_records_it_done() {
        ChatPlan plan = proposeSave();

        ChatPlan after = approve(plan);

        verify(saveJob).saveJob(her, postingId);
        assertThat(only(after).state()).isEqualTo(State.DONE);
        assertThat(only(after).decidedAt()).isEqualTo(NOW);
        assertThat(only(after).finishedAt()).isEqualTo(NOW);
    }

    @Test
    void a_rejected_proposal_changes_nothing_and_cannot_be_approved_after() {
        ChatPlan plan = proposeSave();

        ChatPlan rejected = service.reject(her, plan.id(), only(plan).id());
        ChatPlan after = approve(plan);

        assertThat(only(rejected).state()).isEqualTo(State.REJECTED);
        assertThat(only(after).state()).isEqualTo(State.REJECTED);
        verifyNoInteractions(saveJob);
    }

    @Test
    void a_second_click_does_not_run_the_change_again() {
        ChatPlan plan = proposeSave();

        approve(plan);
        ChatPlan replay = approve(plan);
        service.reject(her, plan.id(), only(plan).id());

        verify(saveJob, times(1)).saveJob(her, postingId);
        assertThat(only(replay).state()).isEqualTo(State.DONE);
    }

    @Test
    void someone_else_cannot_approve_or_reject_her_proposal() {
        ChatPlan plan = proposeSave();

        assertThatThrownBy(() -> approval.approve(someoneElse, plan.id(), only(plan).id(),
                GenerationSettings.DEFAULTS))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.reject(someoneElse, plan.id(), only(plan).id()))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(saveJob);
        assertThat(only(plans.findById(plan.id()).orElseThrow()).state()).isEqualTo(State.PENDING);
    }

    @Test
    void approval_checks_the_application_is_still_hers() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.RECRUITER_CONTACT)));
        when(applications.findByIdAndUserId(applicationId, her)).thenReturn(Optional.empty());

        ChatPlan after = approve(plan);

        assertThat(only(after).state()).isEqualTo(State.FAILED);
        assertThat(only(after).failure()).isEqualTo(PlanFailure.APPLICATION_GONE);
        verifyNoInteractions(updateStatus);
    }

    @Test
    void a_status_change_proposed_before_she_moved_the_application_herself_fails_as_stale() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.REJECTED)));
        givenHerApplicationIs(ApplicationStatus.RECRUITER_CONTACT, null);

        ChatPlan after = approve(plan);

        assertThat(only(after).failure()).isEqualTo(PlanFailure.CHANGED_SINCE);
        verifyNoInteractions(updateStatus);
    }

    @Test
    void a_posting_removed_since_the_proposal_fails_as_gone() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.posting(PlanItem.Kind.IGNORE_POSTING, postingId)));
        when(jobs.findById(postingId)).thenReturn(Optional.empty());

        ChatPlan after = approve(plan);

        assertThat(only(after).failure()).isEqualTo(PlanFailure.POSTING_GONE);
        verifyNoInteractions(ignoreJob);
    }

    @Test
    void a_status_change_keeps_her_notes() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.RECRUITER_CONTACT)));

        approve(plan);

        verify(updateStatus).updateStatus(applicationId, her, ApplicationStatus.RECRUITER_CONTACT, null);
    }

    @Test
    void logging_an_interview_keeps_her_deadline() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.interview(applicationId, INTERVIEW)));

        ChatPlan after = approve(plan);

        verify(updateDates).updateDates(applicationId, her, DEADLINE, INTERVIEW);
        assertThat(only(after).state()).isEqualTo(State.DONE);
    }

    @Test
    void a_change_the_app_refuses_at_approval_is_recorded_as_not_allowed() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.REJECTED)));
        when(updateStatus.updateStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Invalid status transition"));

        ChatPlan after = approve(plan);

        assertThat(only(after).state()).isEqualTo(State.FAILED);
        assertThat(only(after).failure()).isEqualTo(PlanFailure.NOT_ALLOWED);
    }

    @Test
    void an_interview_moved_by_her_since_the_proposal_fails_as_stale() {
        ChatPlan plan = service.propose(her, chatId,
                List.of(ProposedChange.interview(applicationId, INTERVIEW)));
        givenHerApplicationIs(ApplicationStatus.APPLIED, INTERVIEW.plusDays(1));

        ChatPlan after = approve(plan);

        assertThat(only(after).failure()).isEqualTo(PlanFailure.CHANGED_SINCE);
        verifyNoInteractions(updateDates);
    }

    @Test
    void the_model_cannot_propose_changes_to_someone_elses_application() {
        when(applications.findByIdAndUserId(applicationId, her)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.REJECTED))))
                .isInstanceOf(ProposalRefusedException.class);
    }

    @Test
    void a_status_move_the_app_does_not_allow_is_never_proposed() {
        assertThatThrownBy(() -> service.propose(her, chatId,
                List.of(ProposedChange.status(applicationId, ApplicationStatus.OFFER))))
                .isInstanceOf(ProposalRefusedException.class)
                .hasMessageContaining("RECRUITER_CONTACT");
    }

    @Test
    void an_interview_time_the_app_cannot_show_is_never_proposed() {
        LocalDateTime farAway = LocalDateTime.parse("+10000-01-01T00:00");

        assertThatThrownBy(() -> service.propose(her, chatId,
                List.of(ProposedChange.interview(applicationId, farAway))))
                .isInstanceOf(ProposalRefusedException.class);
    }

    @Test
    void a_change_cut_off_by_a_restart_is_recorded_as_interrupted_not_left_running() {
        ChatPlan plan = proposeSave();
        plans.move(only(plan).id(), State.PENDING, State.RUNNING, NOW);

        service.recoverInterrupted(NOW.plusSeconds(1));

        PlanItem after = only(plans.findById(plan.id()).orElseThrow());
        assertThat(after.state()).isEqualTo(State.FAILED);
        assertThat(after.failure()).isEqualTo(PlanFailure.INTERRUPTED);
    }

    @Test
    void a_change_approved_by_this_run_before_the_sweep_is_not_marked_interrupted() {
        ChatPlan plan = proposeSave();
        plans.move(only(plan).id(), State.PENDING, State.RUNNING, NOW);

        service.recoverInterrupted(NOW.minusSeconds(1));

        assertThat(only(plans.findById(plan.id()).orElseThrow()).state()).isEqualTo(State.RUNNING);
    }

    @Test
    void approving_documents_answers_at_once_and_writes_them_afterwards_exactly_once() {
        when(applicationFor.find(her, postingId)).thenReturn(applicationId);
        ChatPlan plan = proposeDocuments(postingId);

        ChatPlan answered = approve(plan);
        approve(plan);

        assertThat(only(answered).state()).isEqualTo(State.RUNNING);
        verifyNoInteractions(generation);
        queued.forEach(Runnable::run);
        verify(generation, times(1)).write(eq(her), eq(postingId), any());
        PlanItem done = only(plans.findById(plan.id()).orElseThrow());
        assertThat(done.state()).isEqualTo(State.DONE);
        assertThat(done.resultApplicationId()).isEqualTo(applicationId);
    }

    @Test
    void approving_a_whole_batch_writes_each_waiting_posting_once_and_skips_one_she_rejected() {
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        givenPosting(second);
        givenPosting(third);
        ChatPlan plan = proposeDocuments(postingId, second, third);
        service.reject(her, plan.id(), plan.items().get(1).id());

        GenerationSettings shortOnes = new GenerationSettings("SHORT", null, null, null);
        approval.approvePending(her, plan.id(), shortOnes);
        approval.approvePending(her, plan.id(), GenerationSettings.DEFAULTS);
        queued.forEach(Runnable::run);

        verify(generation).write(her, postingId, shortOnes);
        verify(generation).write(her, third, shortOnes);
        verify(generation, never()).write(eq(her), eq(second), any());
    }

    @Test
    void documents_that_failed_partway_are_failed_but_still_point_at_the_application() {
        when(applicationFor.find(her, postingId)).thenReturn(applicationId);
        org.mockito.Mockito.doThrow(new IllegalStateException("timeout"))
                .when(generation).write(any(), any(), any());
        ChatPlan plan = proposeDocuments(postingId);

        approve(plan);
        queued.forEach(Runnable::run);

        PlanItem after = only(plans.findById(plan.id()).orElseThrow());
        assertThat(after.failure()).isEqualTo(PlanFailure.FAILED);
        assertThat(after.resultApplicationId()).isEqualTo(applicationId);
    }

    @Test
    void documents_are_not_proposed_for_a_posting_with_no_text_to_tailor_to() {
        UUID bare = UUID.randomUUID();
        when(jobs.findById(bare)).thenReturn(Optional.of(
                Job.builder().id(bare).title("Opslag").companyName("Firma").build()));

        assertThatThrownBy(() -> proposeDocuments(bare)).isInstanceOf(ProposalRefusedException.class);
    }

    @Test
    void documents_are_not_written_onto_an_application_she_has_already_sent() {
        ChatPlan plan = proposeDocuments(postingId);
        Application sent = new Application(applicationId, her, postingId, ApplicationStatus.APPLIED,
                null, null, null, null, null, null, null, null, null, null, null, NOW, NOW, null,
                null, DEADLINE, null);
        when(applications.findByUserId(her)).thenReturn(List.of(sent));

        ChatPlan after = approve(plan);

        assertThat(only(after).failure()).isEqualTo(PlanFailure.CHANGED_SINCE);
        assertThatThrownBy(() -> proposeDocuments(postingId)).isInstanceOf(ProposalRefusedException.class);
        verifyNoInteractions(generation);
    }

    @Test
    void rejecting_a_whole_batch_rejects_only_what_still_waits() {
        UUID second = UUID.randomUUID();
        givenPosting(second);
        ChatPlan plan = proposeDocuments(postingId, second);
        approval.approve(her, plan.id(), plan.items().get(0).id(), GenerationSettings.DEFAULTS);

        ChatPlan after = service.rejectPending(her, plan.id());

        assertThat(after.items()).extracting(PlanItem::state)
                .containsExactly(State.RUNNING, State.REJECTED);
    }

    private ChatPlan proposeDocuments(UUID... postings) {
        return service.propose(her, chatId, Arrays.stream(postings)
                .map(id -> ProposedChange.posting(PlanItem.Kind.GENERATE_DOCUMENTS, id))
                .toList());
    }

    private void givenPosting(UUID id) {
        when(jobs.findById(id)).thenReturn(Optional.of(
                Job.builder().id(id).title("Opslag").companyName("Firma").descriptionClean(POSTING_TEXT)
                        .build()));
    }

    private ChatPlan proposeSave() {
        return service.propose(her, chatId,
                List.of(ProposedChange.posting(PlanItem.Kind.SAVE_POSTING, postingId)));
    }

    private ChatPlan approve(ChatPlan plan) {
        return approval.approve(her, plan.id(), only(plan).id(), GenerationSettings.DEFAULTS);
    }

    private static PlanItem only(ChatPlan plan) {
        assertThat(plan.items()).hasSize(1);
        return plan.items().get(0);
    }

    private void givenHerApplicationIs(ApplicationStatus status, LocalDateTime interviewAt) {
        Application app = new Application(applicationId, her, postingId, status, null, null, null,
                null, null, null, null, null, null, null, "hendes noter", NOW, NOW, null, null,
                DEADLINE, interviewAt);
        when(applications.findByIdAndUserId(applicationId, her)).thenReturn(Optional.of(app));
        when(getApplication.getApplicationById(applicationId, her)).thenReturn(Optional.of(app));
    }
}
