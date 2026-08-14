package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Material;
import com.smartstudy.planning.model.MaterialStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByCourseIdAndUserIdOrderByUploadedAtAsc(UUID courseId, String userId);

    long countByCourseIdAndUserId(UUID courseId, String userId);

    Optional<Material> findByIdAndUserId(UUID id, String userId);

    void deleteByIdAndCourseIdAndUserId(UUID id, UUID courseId, String userId);

    Optional<Material> findFirstByStatusOrderByUploadedAtAsc(MaterialStatus status);

    Optional<Material> findFirstByStatusAndRetryCountLessThanOrderByUploadedAtAsc(
            MaterialStatus status, int maxRetries);

    Optional<Material> findByIdAndUserIdAndStatus(UUID id, String userId, MaterialStatus status);

    List<Material> findByCourseId(UUID courseId);

    List<Material> findByIdIn(java.util.Collection<UUID> ids);
}
