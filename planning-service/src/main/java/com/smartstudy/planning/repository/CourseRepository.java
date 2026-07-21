package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByUserIdAndHiddenFalseOrderByCreatedAtAsc(String userId);

    List<Course> findByUserIdOrderByCreatedAtAsc(String userId);

    Optional<Course> findByIdAndUserId(UUID id, String userId);

    List<Course> findTop5ByUserIdAndExamDateAfterOrderByExamDateAsc(String userId, Instant now);
}
