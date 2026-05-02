package com.sitegenius.hrms.repository;

import com.sitegenius.hrms.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── Called by NotificationService.getUnreadCount() ────────────────────────
    // Spring Data derives: WHERE recipientId = ? AND isRead = false
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // ── Called by NotificationService.getRecent() ─────────────────────────────
    // Returns top 20 ordered by createdAt DESC
    List<Notification> findTop20ByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    // ── Called by NotificationService.getPaginated() ──────────────────────────
    // Spring Data derives: WHERE recipientId = ? ORDER BY createdAt DESC (via Pageable)
    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    // ── Called by NotificationService.markAllRead() ───────────────────────────
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE n.recipientId = :userId AND n.isRead = false")
    int markAllReadForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    // ── Called by NotificationService.markOneRead() ───────────────────────────
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now " +
            "WHERE n.id = :id AND n.recipientId = :userId")
    int markOneReadById(@Param("id") Long id,
                        @Param("userId") Long userId,
                        @Param("now") LocalDateTime now);

    // ── Cleanup old read notifications (>30 days) — used by scheduler ─────────
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.readAt < :cutoff")
    int deleteOldRead(@Param("cutoff") LocalDateTime cutoff);
}