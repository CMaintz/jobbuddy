package com.autoapplicant.usecase.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autoapplicant.domain.chat.ChatAction;
import com.autoapplicant.domain.chat.ChatAction.Kind;
import com.autoapplicant.domain.job.Job;
import com.autoapplicant.port.out.job.JobRepositoryPort;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatActionServiceTest {

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final Map<UUID, Job> stored = Map.of(
            first, job(first, "Kommunikationskonsulent", "Aarhus Kommune"),
            second, job(second, "Presserådgiver", "Region Midt"));
    private final JobRepositoryPort jobs = mock(JobRepositoryPort.class);
    private final ChatActionService actions = new ChatActionService(jobs);

    private static Job job(UUID id, String title, String company) {
        return Job.builder().id(id).title(title).companyName(company).build();
    }

    @BeforeEach
    void givenStoredPostings() {
        when(jobs.findByIds(anyList())).thenAnswer(inv -> ((Collection<?>) inv.getArgument(0))
                .stream().map(stored::get).filter(Objects::nonNull).toList());
    }

    @Test
    void a_posting_button_carries_the_title_from_the_database() {
        ChatAction open = actions.resolve(Kind.OPEN_POSTING, List.of(first)).orElseThrow();

        assertThat(open.postings()).containsExactly(
                new ChatAction.Posting(first, "Kommunikationskonsulent", "Aarhus Kommune"));
    }

    @Test
    void a_made_up_posting_never_becomes_a_button() {
        assertThat(actions.resolve(Kind.OPEN_POSTING, List.of(UUID.randomUUID()))).isEmpty();
        assertThat(actions.resolve(Kind.STEP_THROUGH, List.of(first, UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    void each_kind_takes_its_own_number_of_postings() {
        assertThat(actions.resolve(Kind.OPEN_POSTING, List.of(first, second))).isEmpty();
        assertThat(actions.resolve(Kind.START_APPLICATION, List.of())).isEmpty();
        assertThat(actions.resolve(Kind.SHOW_PIPELINE, List.of(first))).isEmpty();
        assertThat(actions.resolve(Kind.SHOW_PIPELINE, List.of())).isPresent();
        assertThat(actions.resolve(Kind.STEP_THROUGH, List.of())).isEmpty();
    }

    @Test
    void stepping_through_keeps_her_order_without_repeats_and_has_a_ceiling() {
        ChatAction steps = actions.resolve(Kind.STEP_THROUGH, List.of(second, first, second))
                .orElseThrow();
        assertThat(steps.postingIds()).containsExactly(second, first);

        List<UUID> eleven = Stream.generate(UUID::randomUUID).limit(11).toList();
        assertThat(actions.resolve(Kind.STEP_THROUGH, eleven)).isEmpty();
    }
}
