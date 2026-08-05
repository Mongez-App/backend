package com.smartstudy.identity.repository;

import com.smartstudy.identity.model.JoinRequest;
import com.smartstudy.identity.model.JoinRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    @Query("SELECT jr FROM JoinRequest jr WHERE jr.userId = :userId ORDER BY jr.appliedAt DESC")
    List<JoinRequest> findByUserId(@Param("userId") String userId);

    @Query("SELECT jr FROM JoinRequest jr WHERE jr.team.id = :teamId AND jr.status = :status")
    List<JoinRequest> findByTeamIdAndStatus(@Param("teamId") String teamId, @Param("status") JoinRequestStatus status);

    @Query("SELECT jr FROM JoinRequest jr WHERE jr.inviteCode = :inviteCode")
    Optional<JoinRequest> findByInviteCode(@Param("inviteCode") String inviteCode);

    @Query("SELECT CASE WHEN COUNT(jr) > 0 THEN true ELSE false END FROM JoinRequest jr WHERE jr.team.id = :teamId AND jr.userId = :userId AND jr.status = :status")
    boolean existsByTeamIdAndUserIdAndStatus(@Param("teamId") String teamId, @Param("userId") String userId, @Param("status") JoinRequestStatus status);
}