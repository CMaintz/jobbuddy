package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** One user's research notes on one company. */
@Entity
@Table(name = "user_company_notes")
public class UserCompanyNoteEntity {

    @EmbeddedId
    private Key id;

    @Column(nullable = false, columnDefinition = "text")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCompanyNoteEntity() {}

    public UserCompanyNoteEntity(Key id) { this.id = id; }

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Embeddable
    public record Key(@Column(name = "user_id") UUID userId,
                      @Column(name = "company_id") UUID companyId) implements Serializable {}
}
