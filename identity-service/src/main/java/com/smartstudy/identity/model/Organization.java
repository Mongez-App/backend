package com.smartstudy.identity.model;

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
@Table(name = "organizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @Column(length = 128, nullable = false)
    private String id;

    @Column(length = 255, nullable = false, unique = true)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "established_at")
    private Instant establishedAt;

    @Column(name = "no_of_students", nullable = false)
    @Builder.Default
    private Integer noOfStudents = 0;

    @Column(name = "no_of_courses", nullable = false)
    @Builder.Default
    private Integer noOfCourses = 0;

    @Column(name = "no_of_teams", nullable = false)
    @Builder.Default
    private Integer noOfTeams = 0;

    @Column(name = "is_verified_org", nullable = false)
    @Builder.Default
    private boolean isVerifiedOrg = false;

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
