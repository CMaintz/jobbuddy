package com.autoapplicant.adapter.persistence.entity;

import com.autoapplicant.domain.chat.PlanItem;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Type;

/** State moves only through the repository's conditional updates, never by saving this. */
@Entity
@Table(name = "chat_plan_items")
public class ChatPlanItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "kind", nullable = false, updatable = false)
    private String kind;

    @Type(JsonType.class)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false, updatable = false)
    private PlanItem.Details details;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "failure")
    private String failure;

    @Column(name = "result_application_id")
    private UUID resultApplicationId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public PlanItem.Details getDetails() { return details; }
    public void setDetails(PlanItem.Details details) { this.details = details; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getFailure() { return failure; }
    public UUID getResultApplicationId() { return resultApplicationId; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
