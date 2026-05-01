package com.finabits.hrms.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipientId"),
        @Index(name = "idx_notif_recipient_read", columnList = "recipientId, isRead")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who receives this notification */
    @Column(nullable = false)
    private Long recipientId;

    @Column
    private Long senderId;

    /**
     * Type codes: MISSED_CHECKIN, TASK_ASSIGNED, TASK_COMMENT, TASK_STATUS,
     * LEAVE_APPLIED, LEAVE_APPROVED, LEAVE_REJECTED,
     * ANNOUNCEMENT, HOLIDAY_ADDED, HOLIDAY_REMOVED
     */
    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 600)
    private String message;

    /** ID of the related entity (task id, leave id, etc.) */
    @Column
    private Long referenceId;

    /** TASK, LEAVE, ATTENDANCE, ANNOUNCEMENT, HOLIDAY */
    @Column(length = 60)
    private String referenceType;

    /**
     * Use primitive boolean + explicit @JsonProperty to guarantee
     * JSON field name is always "isRead" (not "read").
     * Lombok on primitive boolean generates isRead() which Jackson
     * maps correctly as "isRead".
     */
    @Column(nullable = false)
    @Builder.Default
    @JsonProperty("isRead")
    private boolean isRead = false;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime readAt;
}