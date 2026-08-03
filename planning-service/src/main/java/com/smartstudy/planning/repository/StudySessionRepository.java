package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    Optional<StudySession> findByIdAndUserId(UUID id, String userId);

    List<StudySession> findByUserId(String userId);

    List<StudySession> findByUserIdAndEndedAtBetween(String userId, Instant start, Instant end);
}
