package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.StudyBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StudyBlockRepository extends JpaRepository<StudyBlock, UUID> {

    List<StudyBlock> findByUserIdAndScheduledDateBetweenOrderByScheduledDateAscCreatedAtAsc(
            String userId,
            LocalDate startDate,
            LocalDate endDate);

    List<StudyBlock> findByIdInAndUserId(Collection<UUID> ids, String userId);

    void deleteByCourseIdAndUserId(UUID courseId, String userId);
}
