package com.smartstudy.planning.repository;

import com.smartstudy.planning.enums.TeamMemberStatus;
import com.smartstudy.planning.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, java.util.UUID> {

    Optional<TeamMember> findByTeamIdAndUserId(String teamId, String userId);

    List<TeamMember> findByUserIdAndStatus(String userId, TeamMemberStatus status);

    List<TeamMember> findByTeamIdAndStatus(String teamId, TeamMemberStatus status);

    long countByTeamIdAndStatus(String teamId, TeamMemberStatus status);

    boolean existsByTeamIdAndUserIdAndStatus(String teamId, String userId, TeamMemberStatus status);

    List<TeamMember> findByUserIdAndStatusIn(String userId, List<TeamMemberStatus> statuses);
}
