package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.TaskDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskDelegationRepository extends JpaRepository<TaskDelegation, Long> {

    /** Full chain for a task, oldest → newest */
    List<TaskDelegation> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    /** Latest delegation record for a task (most recent hop) */
    Optional<TaskDelegation> findTopByTaskIdOrderByCreatedAtDesc(Long taskId);

    /**
     * Latest DELEGATED hop — used when a delegatee submits so we can find
     * who the task should be RETURNED to.
     */
    @Query("SELECT d FROM TaskDelegation d " +
            "WHERE d.task.id = :taskId " +
            "AND d.type = com.sitegenius.hrms.entity.TaskDelegation$DelegationType.DELEGATED " +
            "ORDER BY d.createdAt DESC")
    List<TaskDelegation> findDelegatedHopsByTaskDesc(@Param("taskId") Long taskId);

    /** Count total hops for a task */
    long countByTaskId(Long taskId);

    /** Check if a specific user is in the active delegation chain for a task */
    @Query("SELECT COUNT(d) > 0 FROM TaskDelegation d " +
            "WHERE d.task.id = :taskId " +
            "AND (d.fromUser.id = :userId OR d.toUser.id = :userId)")
    boolean isUserInChain(@Param("taskId") Long taskId, @Param("userId") Long userId);
}