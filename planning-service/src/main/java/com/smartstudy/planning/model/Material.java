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

    @Column(nullable = false)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialStatus status;

    private String filePath;

    private String deviceFileUri;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    private int retryCount = 0;

    private Instant processingStartedAt;

    private Instant processedAt;

    @Column(nullable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now();
    }
}
