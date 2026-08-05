package com.smartstudy.identity.repository;

import com.smartstudy.identity.model.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, String> {

    @Query("SELECT t FROM Team t JOIN TeamMembership tm ON t.id = tm.id.teamId WHERE tm.id.userId = :userId")
    List<Team> findByUserId(@Param("userId") String userId);

    @Query("SELECT t FROM Team t WHERE t.isPublic = true")
    Page<Team> findPublicTeams(Pageable pageable);

    @Query("SELECT t FROM Team t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.organization.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Team> searchTeams(@Param("query") String query, Pageable pageable);

    Optional<Team> findById(String id);

    Optional<Team> findByInviteCode(String inviteCode);
}