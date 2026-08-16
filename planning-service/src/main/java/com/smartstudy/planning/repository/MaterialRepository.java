package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialIndexingStatus;
import com.smartstudy.planning.model.MaterialStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByCourseIdAndUserIdOrderByUploadedAtAsc(UUID courseId, String userId);

    long countByCourseIdAndUserId(UUID courseId, String userId);

    Optional<Material> findByIdAndUserId(UUID id, String userId);

    void deleteByIdAndCourseIdAndUserId(UUID id, UUID courseId, String userId);

    /**
     * Next material awaiting RAG indexing, oldest first.
     * <p>
     * Requires {@code filePath} to be set: the two-step upload registers metadata
     * before the file arrives, and the pipeline cannot read a PDF that is not on
     * disk yet. A null {@code indexingStatus} is treated as PENDING so materials
     * that predate the column are picked up rather than stranded.
     * </p>
     */
    @Query("""
            SELECT m FROM Material m
            WHERE m.filePath IS NOT NULL
              AND (m.indexingStatus IS NULL OR m.indexingStatus = :pending)
            ORDER BY m.uploadedAt ASC
            """)
    List<Material> findAwaitingIndexing(@Param("pending") MaterialIndexingStatus pending,
                                        Pageable pageable);

    /** Materials whose indexing failed and still have retries left, oldest first. */
    @Query("""
            SELECT m FROM Material m
            WHERE m.indexingStatus = :failed
              AND m.retryCount < :maxRetries
            ORDER BY m.uploadedAt ASC
            """)
    List<Material> findRetryableIndexing(@Param("failed") MaterialIndexingStatus failed,
                                         @Param("maxRetries") int maxRetries,
                                         Pageable pageable);

    Optional<Material> findByIdAndUserIdAndStatus(UUID id, String userId, MaterialStatus status);

    List<Material> findByCourseId(UUID courseId);

    List<Material> findByIdIn(java.util.Collection<UUID> ids);
}
