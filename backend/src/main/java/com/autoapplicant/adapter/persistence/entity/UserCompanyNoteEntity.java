package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One user's research notes on one company. */
@Entity
@Table(name = "user_company_notes")
@IdClass(UserCompanyNoteEntity.Key.class)
public class UserCompanyNoteEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, columnDefinition = "text")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Key implements Serializable {
        private UUID userId;
        private UUID companyId;

        public Key() {}
        public Key(UUID userId, UUID companyId) { this.userId = userId; this.companyId = companyId; }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(companyId, key.companyId);
        }
        @Override public int hashCode() { return Objects.hash(userId, companyId); }
    }
}
