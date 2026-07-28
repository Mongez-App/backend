package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByUserIdAndCourseIdAndStartDateAfterAndTaskIdIsNotNull(
            String userId, UUID courseId, Instant startDate);

    List<Event> findByUserIdAndCourseIdAndTaskIdIsNotNull(String userId, UUID courseId);
}
