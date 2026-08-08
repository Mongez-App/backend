package com.smartstudy.identity.repository;

import com.smartstudy.identity.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, String> {
    Optional<Organization> findById(String id);
    boolean existsById(String id);
}
