package com.autoapplicant.adapter.persistence.entity;

import com.autoapplicant.domain.chat.ChatAction;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "role", nullable = false, updatable = false)
    private String role;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Type(JsonType.class)
    @Column(name = "actions", columnDefinition = "jsonb", nullable = false)
    private List<ChatAction> actions = new ArrayList<>();

    public UUID getId() { return id; }
    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<ChatAction> getActions() { return actions; }
    public void setActions(List<ChatAction> actions) { this.actions = new ArrayList<>(actions); }
}
