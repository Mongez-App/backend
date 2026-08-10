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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 128, nullable = false)
    private String id;

    @Column(length = 255, nullable = false, unique = true)
    private String email;

    @Column(length = 255)
    private String name;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "is_guest", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean isGuest = false;

    @Column(length = 20, nullable = false)
    @ColumnDefault("'SYSTEM'")
    @Builder.Default
    private String appearance = "SYSTEM";

    @Column(length = 20, nullable = false)
    @ColumnDefault("'en'")
    @Builder.Default
    private String language = "en";

    @Column(name = "total_study_hours", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer totalStudyHours = 0;

    @Column(name = "completed_tasks_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer completedTasksCount = 0;

    @Column(name = "current_streak_days", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer currentStreakDays = 0;

    @Column(name = "calendar_connected", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean calendarConnected = false;

    @Column(name = "calendar_synced", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean calendarSynced = false;

    @Column(name = "last_calendar_sync_at")
    private Instant lastCalendarSyncAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
