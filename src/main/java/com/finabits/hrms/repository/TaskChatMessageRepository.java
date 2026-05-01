package com.finabits.hrms.repository;

import com.finabits.hrms.entity.TaskChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskChatMessageRepository extends JpaRepository<TaskChatMessage, Long> {

    /**
     * All messages for a task ordered oldest → newest.
     * New members joining later will get full history via this query.
     */
    List<TaskChatMessage> findByTaskIdOrderByCreatedAtAsc(Long taskId);

    /** Count messages in a task chat */
    long countByTaskId(Long taskId);

    /**
     * Recent N messages — used for quick preview.
     * Spring Data handles LIMIT via Pageable, but for simplicity using native:
     */
    @Query(value = "SELECT * FROM task_chat_messages WHERE task_id = :taskId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<TaskChatMessage> findRecentMessages(@Param("taskId") Long taskId, @Param("limit") int limit);

    /** All file messages for a task (type = FILE or IMAGE) */
    @Query("SELECT m FROM TaskChatMessage m WHERE m.task.id = :taskId AND m.messageType IN ('FILE', 'IMAGE') ORDER BY m.createdAt DESC")
    List<TaskChatMessage> findFileMessages(@Param("taskId") Long taskId);
}