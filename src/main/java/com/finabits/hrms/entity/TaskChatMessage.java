package com.sitegenius.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * TaskChatMessage — stores every chat message (text / file / image) for a task.
 *
 * File handling:
 *   - Files are saved to C:/hrms-uploads/task-chat/{taskId}/
 *   - filePath stores the absolute path on disk
 *   - fileName stores the original filename shown in UI
 *   - fileSize stores size in bytes
 *   - fileType stores MIME type (image/png, application/pdf, etc.)
 *
 * All task members + admin can see all messages (including history when newly added).
 */
@Entity
@Table(
        name = "task_chat_messages",
        indexes = {
                @Index(name = "idx_chat_task_id",      columnList = "task_id"),
                @Index(name = "idx_chat_sender_id",    columnList = "sender_id"),
                @Index(name = "idx_chat_created_at",   columnList = "created_at")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    /** Text content of the message (nullable if message is file-only) */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** Type: TEXT | FILE | IMAGE | SYSTEM */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String messageType = "TEXT";

    // ── File fields (only set when messageType = FILE or IMAGE) ────────────

    /** Original filename as uploaded by user */
    @Column(length = 500)
    private String fileName;

    /** Absolute path on disk: C:/hrms-uploads/task-chat/{taskId}/{uuid_filename} */
    @Column(length = 1000)
    private String filePath;

    /** Size in bytes */
    private Long fileSize;

    /** MIME type: image/png, application/pdf, etc. */
    @Column(length = 200)
    private String fileType;

    /** Whether this message has been edited */
    @Builder.Default
    private boolean edited = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;
}