package com.smartstudy.planning.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdAndScheduledDateOrderByCreatedAtAsc(String userId, LocalDate scheduledDate);

    Optional<Task> findByIdAndUserId(UUID id, String userId);
}
