package com.autoapplicant.adapter.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "structured_document_templates")
public class StructuredDocumentTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "layout_type", nullable = false)
    private String layoutType;

    @Column(name = "export_mode", nullable = false)
    private String exportMode;

    @Column(name = "supports_profile_image", nullable = false)
    private boolean supportsProfileImage;

    @Column(name = "ats_safe", nullable = false)
    private boolean atsSafe;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "default_primary_color", nullable = false)
    private String defaultPrimaryColor;

    @Column(name = "default_accent_color", nullable = false)
    private String defaultAccentColor;

    @Column(name = "default_font_family", nullable = false)
    private String defaultFontFamily;

    @Column(name = "default_font_scale", nullable = false)
    private String defaultFontScale;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }
    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getLayoutType() { return layoutType; }
    public void setLayoutType(String layoutType) { this.layoutType = layoutType; }
    public String getExportMode() { return exportMode; }
    public void setExportMode(String exportMode) { this.exportMode = exportMode; }
    public boolean isSupportsProfileImage() { return supportsProfileImage; }
    public void setSupportsProfileImage(boolean supportsProfileImage) { this.supportsProfileImage = supportsProfileImage; }
    public boolean isAtsSafe() { return atsSafe; }
    public void setAtsSafe(boolean atsSafe) { this.atsSafe = atsSafe; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getDefaultPrimaryColor() { return defaultPrimaryColor; }
    public void setDefaultPrimaryColor(String defaultPrimaryColor) { this.defaultPrimaryColor = defaultPrimaryColor; }
    public String getDefaultAccentColor() { return defaultAccentColor; }
    public void setDefaultAccentColor(String defaultAccentColor) { this.defaultAccentColor = defaultAccentColor; }
    public String getDefaultFontFamily() { return defaultFontFamily; }
    public void setDefaultFontFamily(String defaultFontFamily) { this.defaultFontFamily = defaultFontFamily; }
    public String getDefaultFontScale() { return defaultFontScale; }
    public void setDefaultFontScale(String defaultFontScale) { this.defaultFontScale = defaultFontScale; }
}
