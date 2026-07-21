package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.CalendarIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CalendarIntegrationRepository extends JpaRepository<CalendarIntegration, UUID> {
    Optional<CalendarIntegration> findByUserId(String userId);
}
