package com.finabits.hrms.service;

import com.finabits.hrms.entity.Notification;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.repository.NotificationRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate  messagingTemplate;
    private final UserRepository         userRepository;   // ✅ Correct placement

    // ── Generic create ────────────────────────────────────────────────────────
    @Transactional
    public Notification create(Long recipientId, String type, String title,
                               String message, Long referenceId, String referenceType) {
        Notification n = Notification.builder()
                .recipientId(recipientId)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        n = notificationRepository.save(n);
        broadcastToUser(recipientId, n);
        return n;
    }

    // ── Task helpers ──────────────────────────────────────────────────────────
    @Transactional
    public void taskAssigned(Long recipientId, String taskTitle, Long taskId) {
        create(recipientId, "TASK_ASSIGNED",
                "📋 New Task Assigned",
                "You have been assigned to task: \"" + taskTitle + "\"",
                taskId, "TASK");
    }

    @Transactional
    public void taskComment(Long recipientId, String commenterName,
                            String taskTitle, Long taskId) {
        create(recipientId, "TASK_COMMENT",
                "💬 New Comment",
                commenterName + " commented on task: \"" + taskTitle + "\"",
                taskId, "TASK");
    }

    @Transactional
    public void taskStatusChanged(Long recipientId, String taskTitle,
                                  String newStatus, Long taskId) {
        create(recipientId, "TASK_STATUS",
                "📊 Task Status Updated",
                "Task \"" + taskTitle + "\" is now " + newStatus,
                taskId, "TASK");
    }

    @Transactional
    public void taskChatMessage(Long taskId, String taskTitle,
                                String senderName, Long senderId,
                                List<Long> memberIds) {
        for (Long memberId : memberIds) {
            if (memberId.equals(senderId)) continue;
            create(memberId, "TASK_COMMENT",
                    "💬 " + senderName + " sent a message",
                    "In task: \"" + taskTitle + "\"",
                    taskId, "TASK");
        }
    }

    // ── Holiday helpers ───────────────────────────────────────────────────────
    @Transactional
    public void holidayAdded(String holidayName, String dateLabel) {
        List<Long> allUserIds = userRepository.findAllActiveUserIds();
        for (Long userId : allUserIds) {
            create(userId, "HOLIDAY",
                    "🎉 Holiday Announced",
                    "\"" + holidayName + "\" on " + dateLabel,
                    null, "HOLIDAY");
        }
    }

    @Transactional
    public void holidayRemoved(String holidayName) {
        List<Long> allUserIds = userRepository.findAllActiveUserIds();
        for (Long userId : allUserIds) {
            create(userId, "HOLIDAY",
                    "❌ Holiday Cancelled",
                    "\"" + holidayName + "\" has been removed",
                    null, "HOLIDAY");
        }
    }

    // ── Read operations ───────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecent(Long userId) {
        return notificationRepository.findTop20ByRecipientIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getPaginated(Long userId, int page, int size) {
        return notificationRepository.findByRecipientId(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    // ── Mark read ─────────────────────────────────────────────────────────────
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadForUser(userId, LocalDateTime.now());
        broadcastCount(userId, 0L);
    }

    @Transactional
    public void markOneRead(Long notificationId, Long userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (!n.getRecipientId().equals(userId)) return;
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        });
        long remaining = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
        broadcastCount(userId, remaining);
    }

    // ── WebSocket helpers ─────────────────────────────────────────────────────
    private void broadcastToUser(Long userId, Notification notification) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/notifications/" + userId, notification);
            long count = notificationRepository.countByRecipientIdAndIsReadFalse(userId);
            broadcastCount(userId, count);
        } catch (Exception e) {
            log.warn("WS notification push failed for user {}: {}", userId, e.getMessage());
        }
    }
    // ── Leave helpers ─────────────────────────────────────────────────────────

    /**
     * Called when an employee applies for leave — notifies all admins.
     */
    @Transactional
    public void leaveApplied(String employeeName, String leaveType,
                             String startDate, String endDate, Long leaveId) {
        List<Long> adminIds = userRepository.findAllActiveAdminIds();
        for (Long adminId : adminIds) {
            create(adminId, "LEAVE_APPLIED",
                    "📅 New Leave Request",
                    employeeName + " applied for " + leaveType
                            + " from " + startDate + " to " + endDate,
                    leaveId, "LEAVE");
        }
    }

    /**
     * Called when admin approves/rejects a leave — notifies the employee.
     */
    @Transactional
    public void leaveActioned(Long employeeId, String action,
                              String leaveType, Long leaveId) {
        create(employeeId, "LEAVE_" + action.toUpperCase(),
                action.equalsIgnoreCase("APPROVED") ? "✅ Leave Approved" : "❌ Leave Rejected",
                "Your " + leaveType + " request has been " + action.toLowerCase(),
                leaveId, "LEAVE");
    }

    // ── Announcement helpers ──────────────────────────────────────────────────

    /**
     * Called when an admin creates a new announcement.
     * Sends an in-app notification to every active user (employees + admins).
     *
     * @param title          the announcement title
     * @param announcementId the saved announcement's ID (for deep-linking)
     */
    @Transactional
    public void announcement(String title, Long announcementId) {
        List<Long> allUserIds = userRepository.findAllActiveUserIds();
        for (Long userId : allUserIds) {
            create(userId, "ANNOUNCEMENT",
                    "📢 New Announcement",
                    title,
                    announcementId, "ANNOUNCEMENT");
        }
        log.info("Announcement notification '{}' sent to {} users", title, allUserIds.size());
    }

    // ── Attendance helpers ────────────────────────────────────────────────────

    /**
     * Called by MissedCheckinNotificationScheduler when an employee
     * fails to check in within a slot window.
     *
     * @param recipientId  employee's user ID
     * @param employeeName employee's full name (used for logging)
     * @param slotLabel    the slot time string, e.g. "09:00"
     * @param dateLabel    ISO date string, e.g. "2025-06-10"
     */
    @Transactional
    public void missedCheckin(Long recipientId, String employeeName,
                              String slotLabel, String dateLabel) {
        create(recipientId, "MISSED_CHECKIN",
                "⚠️ Missed Check-In",
                "You missed your check-in for the " + slotLabel + " slot on " + dateLabel
                        + ". Please check in as soon as possible.",
                null, "ATTENDANCE");
        log.info("Missed check-in notification sent to {} ({}) for slot {} on {}",
                employeeName, recipientId, slotLabel, dateLabel);
    }
    private void broadcastCount(Long userId, long count) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/notif-count/" + userId,
                    java.util.Map.of("count", count));
        } catch (Exception e) {
            log.warn("WS notif-count push failed for user {}: {}", userId, e.getMessage());
        }
    }
    // Called when employee submits a salary slip request
    @Transactional
    public void salaryRequested(String employeeName, int month, int year, Long requestId) {
        // Notify all admins
        userRepository.findByRole(Role.ADMIN).stream()
                .filter(User::isActive)
                .forEach(admin -> create(
                        admin.getId(), "SALARY_REQUEST",
                        "💰 Salary Slip Request",
                        employeeName + " requested a slip for " + month + "/" + year,
                        requestId, "SALARY"
                ));
    }

    // Called when admin approves or rejects
    @Transactional
    public void salaryActioned(Long employeeId, String status, int month, int year, Long requestId) {
        String title = status.equals("APPROVED") ? "✅ Salary Slip Ready" : "❌ Salary Request Rejected";
        String msg   = status.equals("APPROVED")
                ? "Your salary slip for " + month + "/" + year + " has been generated"
                : "Your salary request for " + month + "/" + year + " was rejected";
        create(employeeId, "SALARY_" + status, title, msg, requestId, "SALARY");
    }
}