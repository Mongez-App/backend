package com.smartstudy.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_messages_task_id", columnList = "taskId"),
    @Index(name = "idx_chat_messages_task_created", columnList = "taskId, createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // --- Assistant-only metadata (null for USER messages) ---
    private Boolean usedContext;

    @Column(length = 10)
    private String confidence;

    @Column(columnDefinition = "TEXT")
    private String suggestedFollowUp;

    @Column(columnDefinition = "TEXT")
    private String sources;  // JSON string: [{"section":"...","page":12}]

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
