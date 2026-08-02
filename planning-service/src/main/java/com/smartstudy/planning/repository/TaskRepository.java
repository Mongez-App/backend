package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdOrderByCreatedAtAsc(String userId);

    List<Task> findByUserIdAndScheduledDateOrderByCreatedAtAsc(String userId, LocalDate scheduledDate);

    List<Task> findByUserIdAndCourseIdOrderByCreatedAtAsc(String userId, UUID courseId);

    List<Task> findByUserIdAndCourseIdAndScheduledDateOrderByCreatedAtAsc(
            String userId, UUID courseId, LocalDate scheduledDate);

    Optional<Task> findByIdAndUserId(UUID id, String userId);

    long countByUserIdAndCourseId(String userId, UUID courseId);

    long countByUserIdAndCourseIdAndCompletedTrue(String userId, UUID courseId);

    long countByUserIdAndScheduledDateAndCompletedTrue(String userId, LocalDate scheduledDate);

    long countByUserIdAndScheduledDate(String userId, LocalDate scheduledDate);
}
