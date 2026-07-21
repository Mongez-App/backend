package com.smartstudy.planning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_integrations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", length = 128, nullable = false, unique = true)
    private String userId;

    @Column(length = 50, nullable = false)
    @Builder.Default
    private String provider = "google_calendar";

    @Column(nullable = false)
    @Builder.Default
    private boolean connected = false;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;
}
