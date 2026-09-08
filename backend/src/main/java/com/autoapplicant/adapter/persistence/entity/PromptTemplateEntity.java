package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prompt_templates")
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "text")
    private String userPrompt;

    @Column(name = "output_constraints", columnDefinition = "text")
    private String outputConstraints;

    @Column(name = "is_public")
    private boolean isPublic;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    /** Exactly one per category: the persona used when the caller names no template. */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    /** App-origin: duplicable, never editable or deletable through the API. */
    @Column(name = "is_protected", nullable = false)
    private boolean isProtected;

    @Column(name = "parent_template_id")
    private UUID parentTemplateId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "usage_count", nullable = false)
    private int usageCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public String getOutputConstraints() { return outputConstraints; }
    public void setOutputConstraints(String outputConstraints) { this.outputConstraints = outputConstraints; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public boolean isSystem() { return isSystem; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    public boolean isProtected() { return isProtected; }
    public void setProtected(boolean isProtected) { this.isProtected = isProtected; }
    public void setSystem(boolean system) { isSystem = system; }
    public UUID getParentTemplateId() { return parentTemplateId; }
    public void setParentTemplateId(UUID parentTemplateId) { this.parentTemplateId = parentTemplateId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
