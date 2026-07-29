package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByUserIdAndCourseIdAndStartDateAfterAndTaskIdIsNotNull(
            String userId, UUID courseId, Instant startDate);

    List<Event> findByUserIdAndCourseIdAndTaskIdIsNotNull(String userId, UUID courseId);
    List<Event> findByUserIdAndStartDateBetween(String userId, Instant startDate, Instant endDate);

    List<Event> findByUserIdAndTaskIdIsNullAndStartDateBetween(String userId, Instant startDate, Instant endDate);
}
