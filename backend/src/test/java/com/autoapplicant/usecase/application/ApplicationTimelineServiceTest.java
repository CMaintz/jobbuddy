package com.autoapplicant.usecase.application;

import com.autoapplicant.domain.analytics.ResponseMetric;
import com.autoapplicant.port.out.analytics.ResponseMetricRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationTimelineServiceTest {

    private final ResponseMetricRepositoryPort repo = mock(ResponseMetricRepositoryPort.class);
    private final ApplicationTimelineService service = new ApplicationTimelineService(repo);

    @Test
    void a_timeline_is_read_for_one_user_only_so_an_id_alone_opens_nothing() {
        UUID applicationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repo.findByApplicationIdAndUserId(applicationId, userId)).thenReturn(List.of(
                new ResponseMetric(UUID.randomUUID(), userId, UUID.randomUUID(), applicationId,
                        "INTERVIEW_SCHEDULED", Instant.parse("2026-03-01T09:00:00Z"), "Second round, Teams")));

        List<ResponseMetric> timeline = service.getTimeline(applicationId, userId);

        assertThat(timeline).singleElement()
                .satisfies(m -> {
                    assertThat(m.eventType()).isEqualTo("INTERVIEW_SCHEDULED");
                    assertThat(m.notes()).isEqualTo("Second round, Teams");
                });
        verify(repo).findByApplicationIdAndUserId(applicationId, userId);
        verify(repo, never()).save(any());
    }

    @Test
    void another_users_application_id_yields_nothing_rather_than_their_history() {
        UUID someoneElsesApplication = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        when(repo.findByApplicationIdAndUserId(someoneElsesApplication, me)).thenReturn(List.of());

        assertThat(service.getTimeline(someoneElsesApplication, me)).isEmpty();
    }
}
