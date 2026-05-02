package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.TaskMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskMemberRepository extends JpaRepository<TaskMember, Long> {

    /** All members of a task */
    List<TaskMember> findByTaskIdOrderByAddedAtAsc(Long taskId);

    /** Check if a user is a member of a task */
    boolean existsByTaskIdAndUserId(Long taskId, Long userId);

    /** Find a specific task-member record */
    Optional<TaskMember> findByTaskIdAndUserId(Long taskId, Long userId);

    /** All task IDs where userId is a member */
    @Query("SELECT tm.task.id FROM TaskMember tm WHERE tm.user.id = :userId")
    List<Long> findTaskIdsByUserId(@Param("userId") Long userId);

    /** Delete a member from a task */
    void deleteByTaskIdAndUserId(Long taskId, Long userId);

    /** Count members on a task */
    long countByTaskId(Long taskId);
}