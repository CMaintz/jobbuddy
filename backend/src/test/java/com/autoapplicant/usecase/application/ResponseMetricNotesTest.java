package com.autoapplicant.usecase.application;

import com.autoapplicant.adapter.persistence.entity.ResponseMetricEntity;
import com.autoapplicant.domain.analytics.ResponseMetric;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A response metric records a real turn in an application — recruiter contact, interview, offer,
 * rejection — and the note is what the candidate wrote about it. The note used to be accepted by
 * the domain record, dropped by the mapper, and read back as null, so the application timeline
 * showed that something happened but never what.
 */
class ResponseMetricNotesTest {

    @Test
    void the_note_survives_the_round_trip() {
        ResponseMetric metric = new ResponseMetric(null, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "REJECTED", Instant.now(),
                "Gik videre med en kandidat med mere Kubernetes-erfaring.");

        ResponseMetricEntity e = new ResponseMetricEntity();
        e.setUserId(metric.userId());
        e.setJobId(metric.jobId());
        e.setApplicationId(metric.applicationId());
        e.setEventType(metric.eventType());
        e.setEventAt(metric.eventAt());
        e.setNotes(metric.notes());

        assertThat(e.getNotes()).isEqualTo("Gik videre med en kandidat med mere Kubernetes-erfaring.");
    }

    @Test
    void a_transition_with_nothing_to_say_carries_no_note() {
        ResponseMetricEntity e = new ResponseMetricEntity();
        e.setNotes(null);
        assertThat(e.getNotes()).isNull();
    }
}
