package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findByCourseIdAndUserIdOrderByUploadedAtAsc(UUID courseId, String userId);

    long countByCourseIdAndUserId(UUID courseId, String userId);

    Optional<Material> findByIdAndUserId(UUID id, String userId);

    void deleteByIdAndCourseIdAndUserId(UUID id, UUID courseId, String userId);
}
