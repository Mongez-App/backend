package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Paginated history retrieval (oldest first) for the GET endpoint.
     */
    Page<ChatMessage> findByTaskIdOrderByCreatedAtAsc(UUID taskId, Pageable pageable);

    /**
     * Load the N most recent messages for prompt context.
     * Returns newest-first; the caller reverses to chronological order.
     */
    List<ChatMessage> findByTaskIdOrderByCreatedAtDesc(UUID taskId, Pageable pageable);

    /**
     * Check whether any messages exist for a task (chat existence check).
     */
    boolean existsByTaskId(UUID taskId);

    /**
     * Delete all messages for a task (DELETE endpoint).
     */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") UUID taskId);

    /**
     * Count messages for a task (used for pagination metadata).
     */
    long countByTaskId(UUID taskId);
}
