package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByUserIdAndCourseIdAndStartDateAfterAndTaskIdIsNotNull(
            String userId, UUID courseId, Instant startDate);

    List<Event> findByUserIdAndCourseIdAndTaskIdIsNotNull(String userId, UUID courseId);
    List<Event> findByUserIdAndStartDateBetween(String userId, Instant startDate, Instant endDate);

    List<Event> findByUserIdAndTaskIdIsNullAndStartDateBetween(String userId, Instant startDate, Instant endDate);

    List<Event> findByUserIdAndTaskIdIsNullAndCourseIdIsNotNullAndStartDateBetween(String userId, Instant startDate, Instant endDate);

    @Query("SELECT e FROM Event e WHERE e.userId = :userId AND e.courseId IS NULL " +
           "AND ((e.endDate IS NULL AND :start < e.startDate AND e.startDate < :end) " +
           "OR (e.endDate IS NOT NULL AND e.startDate < :end AND e.endDate > :start))")
    List<Event> findOverlappingEvents(@Param("userId") String userId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    Optional<Event> findFirstByUserIdAndCourseIdAndStartDateAfterOrderByStartDateAsc(
            String userId, UUID courseId, Instant date);

    List<Event> findByUserId(String userId);

    List<Event> findByUserIdAndStartDateGreaterThanEqual(String userId, Instant startDate);

    List<Event> findByUserIdAndStartDateLessThanEqual(String userId, Instant endDate);

    List<Event> findByCourseIdInAndTaskIdIsNull(List<UUID> courseIds);

    List<Event> findByCourseIdInAndTaskIdIsNotNull(List<UUID> courseIds);

    Optional<Event> findFirstByCourseIdAndStartDateAfterOrderByStartDateAsc(UUID courseId, Instant startDate);
}
