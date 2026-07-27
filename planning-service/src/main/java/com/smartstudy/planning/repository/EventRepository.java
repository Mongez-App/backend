package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByUserIdAndEventDateBetween(String userId, Instant startDate, Instant endDate);
}
