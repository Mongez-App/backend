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
@Table(name = "courses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    private String courseCode;

    private String imageUrl;

    @Column(nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    private Instant examDate;

    @Enumerated(EnumType.STRING)
    private CourseType courseType;

    private String materialUrl;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "team_id", length = 128)
    private String teamId;

    @Column(name = "organization_id", length = 128)
    private String organizationId;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
