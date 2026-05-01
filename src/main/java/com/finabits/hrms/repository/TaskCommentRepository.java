package com.finabits.hrms.repository;

import com.finabits.hrms.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    // All comments for a task, ordered oldest → newest
    // All comments for a task, ordered oldest → newest
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    // Count comments on a task
    long countByTaskId(Long taskId);
}