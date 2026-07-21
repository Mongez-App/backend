package com.smartstudy.identity.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    @Id
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "daily_study_hours", nullable = false)
    @Builder.Default
    private Integer dailyStudyHours = 0;

    @ElementCollection
    @CollectionTable(name = "user_available_days", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "day_of_week", length = 10, nullable = false)
    @Enumerated(EnumType.STRING)
    private List<com.smartstudy.identity.enums.WeekDay> availableDays;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
