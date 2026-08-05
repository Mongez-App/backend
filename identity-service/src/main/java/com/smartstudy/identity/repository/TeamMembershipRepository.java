package com.smartstudy.identity.repository;

import com.smartstudy.identity.model.TeamMembership;
import com.smartstudy.identity.model.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, TeamMembership.TeamMembershipId> {

    @Query("SELECT tm FROM TeamMembership tm WHERE tm.id.userId = :userId")
    List<TeamMembership> findByUserId(@Param("userId") String userId);

    @Query("SELECT tm FROM TeamMembership tm WHERE tm.id.teamId = :teamId")
    List<TeamMembership> findByTeamId(@Param("teamId") String teamId);

    @Query("SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END FROM TeamMembership tm WHERE tm.id.teamId = :teamId AND tm.id.userId = :userId")
    boolean existsByTeamIdAndUserId(@Param("teamId") String teamId, @Param("userId") String userId);

    Optional<TeamMembership> findByIdTeamIdAndIdUserId(String teamId, String userId);

    @Query("SELECT tm FROM TeamMembership tm WHERE tm.id.teamId = :teamId AND tm.role = :role")
    List<TeamMembership> findAdminsByTeamId(@Param("teamId") String teamId, @Param("role") TeamRole role);
}