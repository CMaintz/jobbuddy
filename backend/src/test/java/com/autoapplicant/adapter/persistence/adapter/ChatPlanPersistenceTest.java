package com.autoapplicant.adapter.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.autoapplicant.adapter.persistence.JobRows;
import com.autoapplicant.adapter.persistence.PostgresIntegrationTest;
import com.autoapplicant.domain.application.ApplicationStatus;
import com.autoapplicant.domain.chat.Chat;
import com.autoapplicant.domain.chat.ChatMessage;
import com.autoapplicant.domain.chat.ChatMessage.Role;
import com.autoapplicant.domain.chat.ChatPlan;
import com.autoapplicant.domain.chat.PlanFailure;
import com.autoapplicant.domain.chat.PlanItem;
import com.autoapplicant.domain.chat.PlanItem.State;
import com.autoapplicant.domain.user.User;
import com.autoapplicant.domain.user.UserRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans through V050 on a real Postgres. Committed for real (no test transaction), since the
 * point is what two clicks racing each other do to committed rows. Each test deletes its user
 * after, because other tests in the shared database read every user's data.
 */
@Import({ChatPlanPersistenceAdapter.class, ChatPersistenceAdapter.class, UserPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatPlanPersistenceTest extends PostgresIntegrationTest {

    private static final Instant AT = Instant.parse("2026-11-22T10:00:00Z");
    private static final int RACERS = 8;

    @Autowired ChatPlanPersistenceAdapter plans;
    @Autowired ChatPersistenceAdapter chats;
    @Autowired UserPersistenceAdapter users;
    @Autowired JdbcTemplate jdbc;

    private Chat chat;
    private UUID job;

    @BeforeEach
    void givenAChat() {
        UUID userId = users.save(new User(null, UUID.randomUUID() + "@example.dk", null, null, null,
                UserRole.USER, true, true, null, null)).id();
        chat = chats.save(new Chat(null, userId, UUID.randomUUID(), null, AT, AT));
    }

    @Test
    void a_plan_comes_back_with_what_its_card_promised() {
        PlanItem.Details interview = new PlanItem.Details.Interview(UUID.randomUUID(),
                UUID.randomUUID(), "Rådgiver", "Kommunen", LocalDateTime.of(2026, 12, 1, 9, 0),
                LocalDateTime.of(2026, 12, 3, 10, 30));
        PlanItem.Details status = new PlanItem.Details.StatusMove(UUID.randomUUID(),
                UUID.randomUUID(), "Konsulent", "Arla", ApplicationStatus.APPLIED,
                ApplicationStatus.REJECTED);
        PlanItem.Details posting = new PlanItem.Details.Posting(UUID.randomUUID(), "Udvikler", null);
        PlanItem.Details documents = new PlanItem.Details.Documents(UUID.randomUUID(), "Analytiker",
                "Vestas");

        ChatPlan stored = plans.create(plan(
                PlanItem.proposed(null, PlanItem.Kind.LOG_INTERVIEW, interview),
                PlanItem.proposed(null, PlanItem.Kind.UPDATE_STATUS, status),
                PlanItem.proposed(null, PlanItem.Kind.IGNORE_POSTING, posting),
                PlanItem.proposed(null, PlanItem.Kind.GENERATE_DOCUMENTS, documents)));

        ChatPlan read = plans.findById(stored.id()).orElseThrow();
        assertThat(read.items()).extracting(PlanItem::details)
                .containsExactly(interview, status, posting, documents);
        assertThat(read.items()).extracting(PlanItem::state).containsOnly(State.PENDING);
    }

    /**
     * The column holds every kind's fields in one flat object with no type tag, as rows written
     * before details had a type per kind do; the kind column says which type it reads back as.
     */
    @Test
    void details_keep_the_flat_shape_rows_already_have() {
        UUID posting = UUID.randomUUID();
        UUID application = UUID.randomUUID();
        UUID planId = plans.create(plan(savePosting())).id();
        jdbc.update("INSERT INTO chat_plan_items (plan_id, position, kind, details, state)"
                + " VALUES (?, 1, 'UPDATE_STATUS', ?::jsonb, 'PENDING')", planId,
                "{\"postingId\":\"" + posting + "\",\"applicationId\":\"" + application + "\","
                + "\"title\":\"Konsulent\",\"company\":\"Arla\",\"fromStatus\":\"APPLIED\","
                + "\"toStatus\":\"INTERVIEW\",\"interviewBefore\":null,\"interviewAt\":null}");

        PlanItem read = plans.findById(planId).orElseThrow().items().get(1);

        assertThat(read.details()).isEqualTo(new PlanItem.Details.StatusMove(posting, application,
                "Konsulent", "Arla", ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEW));
        assertThat(jdbc.queryForList("SELECT jsonb_object_keys(details) FROM chat_plan_items"
                + " WHERE plan_id = ? AND position = 0", String.class, planId))
                .containsExactlyInAnyOrder("postingId", "applicationId", "title", "company",
                        "fromStatus", "toStatus", "interviewBefore", "interviewAt");
    }

    @Test
    void written_documents_keep_the_application_they_landed_on() {
        ChatPlan plan = plans.create(plan(PlanItem.proposed(null, PlanItem.Kind.GENERATE_DOCUMENTS,
                new PlanItem.Details.Documents(UUID.randomUUID(), "Rådgiver", "Kommunen"))));
        UUID item = plan.items().get(0).id();
        UUID application = herApplication();

        plans.move(item, State.PENDING, State.RUNNING, AT);
        plans.finish(item, null, application, AT.plusSeconds(60));

        PlanItem read = plans.findById(plan.id()).orElseThrow().items().get(0);
        assertThat(read.state()).isEqualTo(State.DONE);
        assertThat(read.resultApplicationId()).isEqualTo(application);
    }

    @Test
    void after_a_restart_only_items_left_running_by_the_last_run_are_marked_interrupted() {
        ChatPlan plan = plans.create(plan(savePosting(), savePosting(), savePosting()));
        UUID cutOff = plan.items().get(0).id();
        UUID approvedSinceStart = plan.items().get(2).id();
        Instant started = AT.plusSeconds(10);
        plans.move(cutOff, State.PENDING, State.RUNNING, AT);
        plans.move(approvedSinceStart, State.PENDING, State.RUNNING, started.plusSeconds(1));

        plans.failRunning(PlanFailure.INTERRUPTED, started, started.plusSeconds(5));

        ChatPlan read = plans.findById(plan.id()).orElseThrow();
        assertThat(read.items()).extracting(PlanItem::state)
                .containsExactly(State.FAILED, State.PENDING, State.RUNNING);
        assertThat(read.items().get(0).failure()).isEqualTo(PlanFailure.INTERRUPTED);
    }

    @Test
    void of_many_clicks_at_once_exactly_one_approves_the_item() throws Exception {
        UUID item = plans.create(plan(savePosting())).items().get(0).id();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        try {
            List<Future<Boolean>> clicks = new ArrayList<>();
            for (int i = 0; i < RACERS; i++) {
                Callable<Boolean> click = () -> {
                    start.await();
                    return plans.move(item, State.PENDING, State.RUNNING, AT);
                };
                clicks.add(pool.submit(click));
            }
            start.countDown();
            int won = 0;
            for (Future<Boolean> click : clicks) {
                won += click.get() ? 1 : 0;
            }
            assertThat(won).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void a_finished_item_keeps_its_decision_and_result() {
        ChatPlan plan = plans.create(plan(savePosting()));
        UUID item = plan.items().get(0).id();

        plans.move(item, State.PENDING, State.RUNNING, AT);
        plans.finish(item, PlanFailure.POSTING_GONE, null, AT.plusSeconds(1));
        plans.finish(item, null, null, AT.plusSeconds(2));

        PlanItem read = plans.findById(plan.id()).orElseThrow().items().get(0);
        assertThat(read.state()).isEqualTo(State.FAILED);
        assertThat(read.failure()).isEqualTo(PlanFailure.POSTING_GONE);
        assertThat(read.decidedAt()).isEqualTo(AT);
        assertThat(read.finishedAt()).isEqualTo(AT.plusSeconds(1));
    }

    @Test
    void the_record_of_a_plan_outlives_the_chat_it_came_from() {
        ChatPlan plan = plans.create(plan(savePosting()));
        chats.appendMessage(chat.id(), new ChatMessage(Role.ASSISTANT, "Forslag", AT, List.of(),
                List.of(plan.id())));
        assertThat(chats.findMessages(chat.id()).get(0).planIds()).containsExactly(plan.id());

        chats.deleteById(chat.id());

        ChatPlan kept = plans.findById(plan.id()).orElseThrow();
        assertThat(kept.chatId()).isNull();
        assertThat(kept.items()).hasSize(1);
    }

    @AfterEach
    void deleteHerAndAllOfHers() {
        jdbc.update("DELETE FROM users WHERE id = ?", chat.userId());
        if (job != null) {
            jdbc.update("DELETE FROM jobs WHERE id = ?", job);
        }
    }

    /** A real application, since the result column must point at one. */
    private UUID herApplication() {
        job = new JobRows(jdbc).insert("Rådgiver", "Vi søger en rådgiver.", AT);
        return jdbc.queryForObject("INSERT INTO applications (user_id, job_id, status)"
                + " VALUES (?, ?, 'PREPARING') RETURNING id", UUID.class, chat.userId(), job);
    }

    private ChatPlan plan(PlanItem... items) {
        return new ChatPlan(null, chat.userId(), chat.id(), AT, List.of(items));
    }

    private static PlanItem savePosting() {
        return PlanItem.proposed(null, PlanItem.Kind.SAVE_POSTING,
                new PlanItem.Details.Posting(UUID.randomUUID(), "Rådgiver", "Kommunen"));
    }
}
