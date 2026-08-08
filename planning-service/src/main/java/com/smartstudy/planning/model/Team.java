package com.smartstudy.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "teams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @Column(nullable = false, length = 128)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "organization_id", nullable = false, length = 128)
    private String organizationId;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "invite_code", unique = true, length = 64)
    private String inviteCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
