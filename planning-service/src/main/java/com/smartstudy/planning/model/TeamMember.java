package com.smartstudy.planning.model;

import com.smartstudy.planning.enums.TeamMemberStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
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
import java.util.UUID;

@Entity
@Table(name = "team_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @Column(name = "team_id", nullable = false, length = 128)
    private String teamId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private TeamMemberStatus status = TeamMemberStatus.PENDING;

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
