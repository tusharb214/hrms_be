package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.Task;
import com.sitegenius.hrms.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // ── Admin: all tasks (no status filter) ───────────────────────────────────

    @Query(
            value      = "SELECT DISTINCT t FROM Task t ORDER BY t.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t"
    )
    Page<Task> findAllTasksNoFilter(Pageable pageable);

    // ── Admin: all tasks (with status filter) ─────────────────────────────────

    @Query(
            value      = "SELECT DISTINCT t FROM Task t WHERE t.status = :status ORDER BY t.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t WHERE t.status = :status"
    )
    Page<Task> findAllTasks(
            @Param("status") TaskStatus status,
            Pageable pageable);

    // ── Admin: tasks by primary assignee (no status filter) ───────────────────

    @Query(
            value      = "SELECT DISTINCT t FROM Task t WHERE t.assignedTo.id = :userId ORDER BY t.deadline ASC",
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t WHERE t.assignedTo.id = :userId"
    )
    Page<Task> findTasksByUserNoFilter(
            @Param("userId") Long userId,
            Pageable pageable);

    // ── Admin: tasks by primary assignee (with status filter) ─────────────────

    @Query(
            value      = "SELECT DISTINCT t FROM Task t " +
                    "WHERE t.assignedTo.id = :userId AND t.status = :status " +
                    "ORDER BY t.deadline ASC",
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t " +
                    "WHERE t.assignedTo.id = :userId AND t.status = :status"
    )
    Page<Task> findTasksByUser(
            @Param("userId") Long userId,
            @Param("status") TaskStatus status,
            Pageable pageable);

    // ── Employee: my tasks — no status filter ─────────────────────────────────
    // User sees a task if they are in task_members OR are the assignedTo

    @Query(
            value      = "SELECT DISTINCT t FROM Task t " +
                    "LEFT JOIN t.members tm " +
                    "WHERE (t.assignedTo.id = :userId OR tm.user.id = :userId) ",
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t " +
                    "LEFT JOIN t.members tm " +
                    "WHERE (t.assignedTo.id = :userId OR tm.user.id = :userId)"
    )
    Page<Task> findMyTasksAll(
            @Param("userId") Long userId,
            Pageable pageable);

    // ── Employee: my tasks — with status filter ───────────────────────────────

    @Query(
            value      = "SELECT DISTINCT t FROM Task t " +
                    "LEFT JOIN t.members tm " +
                    "WHERE (t.assignedTo.id = :userId OR tm.user.id = :userId) " +
                    "AND t.status = :status " ,
            countQuery = "SELECT COUNT(DISTINCT t) FROM Task t " +
                    "LEFT JOIN t.members tm " +
                    "WHERE (t.assignedTo.id = :userId OR tm.user.id = :userId) " +
                    "AND t.status = :status"
    )
    Page<Task> findMyTasksByStatus(
            @Param("userId") Long userId,
            @Param("status") TaskStatus status,
            Pageable pageable);

    // ── Overdue scheduler ────────────────────────────────────────────────────

    @Query("SELECT t FROM Task t " +
            "WHERE t.deadline < :today " +
            "AND t.status NOT IN ('APPROVED', 'SUBMITTED', 'OVERDUE')")
    List<Task> findTasksToMarkOverdue(@Param("today") LocalDate today);

    // ── Dashboard status counts ───────────────────────────────────────────────

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT t.status, COUNT(DISTINCT t) FROM Task t " +
            "LEFT JOIN t.members tm " +
            "WHERE t.assignedTo.id = :userId OR tm.user.id = :userId " +
            "GROUP BY t.status")
    List<Object[]> countGroupedByStatusForUser(@Param("userId") Long userId);

    @Query("SELECT t FROM Task t WHERE t.status = 'OVERDUE' ORDER BY t.deadline ASC")
    Page<Task> findOverdueTasks(Pageable pageable);
}