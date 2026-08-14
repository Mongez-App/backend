package com.smartstudy.planning.repository;

import com.smartstudy.planning.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, String> {

    Optional<Team> findByInviteCode(String inviteCode);

    List<Team> findByIdIn(List<String> ids);

    List<Team> findByOrganizationIdOrderByCreatedAtAsc(String organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(String organizationId, String name);
}
