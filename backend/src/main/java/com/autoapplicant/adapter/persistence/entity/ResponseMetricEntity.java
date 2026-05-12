package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "response_metrics")
public class ResponseMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_at")
    private Instant eventAt;

    @PrePersist
    void prePersist() {
        if (eventAt == null) eventAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Instant getEventAt() { return eventAt; }
    public void setEventAt(Instant eventAt) { this.eventAt = eventAt; }
}
