package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Entity
@Table(name = "skill_taxonomy")
public class SkillTaxonomyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "parent_id")
    private UUID parentId;

    private String category;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]")
    private String[] aliases;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public UUID getParentId() { return parentId; }
    public String getCategory() { return category; }
    public String[] getAliases() { return aliases; }

    public void setName(String name) { this.name = name; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public void setCategory(String category) { this.category = category; }
    public void setAliases(String[] aliases) { this.aliases = aliases; }
}
