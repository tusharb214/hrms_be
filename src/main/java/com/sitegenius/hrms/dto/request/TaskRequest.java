package com.sitegenius.hrms.dto.request;

import com.sitegenius.hrms.enums.Priority;
import com.sitegenius.hrms.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

public class TaskRequest {

    // ── Create Task — assign to one or more employees (one shared task) ────────
    @Data
    public static class CreateTaskRequest {

        @NotBlank(message = "Title is required and cannot be blank")
        private String title;

        private String description;

        @NotNull(message = "Priority is required (LOW, MEDIUM, HIGH)")
        private Priority priority;

        @NotNull(message = "Deadline is required")
        private LocalDate deadline;

        /** All members to add initially — creates ONE task with all of them */
        @NotEmpty(message = "At least one employee must be selected")
        private List<@Positive(message = "Each user ID must be a positive number") Long> assignedToIds;
    }

    // ── Update Task (admin) ───────────────────────────────────────────────────
    @Data
    public static class UpdateTaskRequest {

        @NotBlank(message = "Title is required and cannot be blank")
        private String title;

        private String description;

        @NotNull(message = "Priority is required (LOW, MEDIUM, HIGH)")
        private Priority priority;

        @NotNull(message = "Deadline is required")
        private LocalDate deadline;
    }

    // ── Add member to existing task (admin) ───────────────────────────────────
    @Data
    public static class AddMemberRequest {

        @NotNull(message = "Employee ID is required")
        @Positive(message = "Employee ID must be a positive number")
        private Long userId;
    }

    // ── Remove member from task (admin) ──────────────────────────────────────
    @Data
    public static class RemoveMemberRequest {

        @NotNull(message = "Employee ID is required")
        @Positive(message = "Employee ID must be a positive number")
        private Long userId;
    }

    // ── Update task status (employee) ─────────────────────────────────────────
    @Data
    public static class UpdateStatusRequest {

        @NotNull(message = "Status is required")
        private TaskStatus status;

        private String comment;
    }

    // ── Admin review (approve / reject) ──────────────────────────────────────
    @Data
    public static class ReviewTaskRequest {

        @NotNull(message = "Review status is required (APPROVED or REJECTED)")
        private TaskStatus status;

        private String comment;
    }

    // ── Add comment (legacy REST comment — kept for backward compat) ──────────
    @Data
    public static class AddCommentRequest {

        @NotBlank(message = "Comment cannot be empty")
        private String comment;
    }

    // ── WebSocket chat message payload (sent via STOMP /app/task/{id}/chat) ──
    @Data
    public static class ChatMessageRequest {

        /** Text content (nullable if this is a file-only message) */
        private String message;

        /**
         * Message type: TEXT | FILE | IMAGE | SYSTEM
         * Defaults to TEXT when only message is provided.
         */
        private String messageType = "TEXT";
    }
}