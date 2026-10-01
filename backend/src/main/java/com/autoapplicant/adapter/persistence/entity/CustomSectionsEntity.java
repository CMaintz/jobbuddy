package com.autoapplicant.adapter.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Type;

/**
 * One row per user holding their custom sections as jsonb: [{id, heading, items:[{id, text}]}].
 * user_id is the natural primary key (one row per user); created_at/updated_at carry DB defaults and
 * aren't mapped because nothing reads them.
 */
@Entity
@Table(name = "custom_sections")
public class CustomSectionsEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> sections;

    public void setUserId(UUID userId) { this.userId = userId; }
    public List<Map<String, Object>> getSections() { return sections; }
    public void setSections(List<Map<String, Object>> sections) { this.sections = sections; }
}
