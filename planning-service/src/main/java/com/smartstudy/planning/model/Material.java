package com.smartstudy.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "materials")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Nullable: org-admin uploads may exist before any course and get linked
    // to one later via createCourse.materialIds.
    @Column
    private UUID courseId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSizeBytes;

    private Integer pageCount;

    // --- Task-generation pipeline state (StudyPlannerAgent) ---
    // Owned exclusively by the agent path. The indexing pipeline below must not
    // write these, or the two race over a single material.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant processingStartedAt;

    private Instant processedAt;

    // --- RAG indexing pipeline state (ScheduledProcessingService) ---
    // Nullable rather than NOT NULL so the column can be added to a table that
    // already has rows; a null value is treated as PENDING when polling.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MaterialIndexingStatus indexingStatus = MaterialIndexingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String indexingErrorMessage;

    private Instant indexedAt;

    @Builder.Default
    private int retryCount = 0;

    private String filePath;

    private String deviceFileUri;

    @Column(nullable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now();
    }
}
