package com.sitegenius.hrms.dto.response;

import com.sitegenius.hrms.enums.Priority;
import com.sitegenius.hrms.enums.TaskStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * All Task Management RESPONSE DTOs.
 */
public class TaskResponseDTO {

    // ─────────────────────────────────────────────────────────────────────────
    // Full task details
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class TaskResponse {

        private Long       id;
        private String     title;
        private String     description;
        private Priority   priority;
        private TaskStatus status;
        private LocalDate  deadline;
        private boolean    overdue;

        // Primary assignee (first member / lead)
        private Long   assignedToId;
        private String assignedToName;

        private Long   createdById;
        private String createdByName;

        // ── Resolution info — "Who resolved / completed this task?" ───────────
        /** Employee who submitted the task (moved status to SUBMITTED) */
        private Long          resolvedById;
        private String        resolvedByName;
        private String        resolvedByDesignation;
        private String        resolvedByDepartment;
        /** When the employee submitted the task */
        private LocalDateTime resolvedAt;

        // ── Approval info — "Who reviewed / approved this task?" ─────────────
        /** Admin who approved or rejected */
        private Long          approvedById;
        private String        approvedByName;
        /** When the admin reviewed */
        private LocalDateTime approvedAt;
        /** Admin's review decision note */
        private String        reviewNote;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        /** All members assigned to this task */
        private List<TaskMemberResponse> members;

        /** Task comments (legacy REST comments) */
        private List<CommentResponse> comments;

        /** Total chat message count */
        private long chatMessageCount;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single task member
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class TaskMemberResponse {

        private Long   userId;
        private String fullName;
        private String employeeCode;
        private String designation;
        private String department;
        private String email;
        private Long   addedById;
        private String addedByName;
        private LocalDateTime addedAt;
        private boolean isPrimary;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat message (Teams-style)
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class ChatMessageResponse {

        private Long          id;
        private Long          taskId;
        private Long          senderId;
        private String        senderName;
        private String        senderRole;
        private String        senderInitials;

        private String        message;
        private String        messageType;

        private String        fileName;
        private Long          fileSize;
        private String        fileType;
        private String        fileDownloadUrl;

        private boolean       edited;
        private LocalDateTime createdAt;
        private LocalDateTime editedAt;

        private boolean       isMine;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Legacy REST comment
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class CommentResponse {

        private Long          id;
        private String        comment;
        private Long          commentedById;
        private String        commentedByName;
        private String        role;
        private LocalDateTime createdAt;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Paginated task list
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class PagedTaskResponse {

        private List<TaskResponse> tasks;
        private int                currentPage;
        private int                totalPages;
        private long               totalItems;
        private int                pageSize;
        private boolean            first;
        private boolean            last;
        private boolean            hasNext;
        private boolean            hasPrevious;
        private Map<String, Long>  statusCounts;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin dashboard
    // ─────────────────────────────────────────────────────────────────────────
    @Data
    public static class TaskDashboardResponse {

        private long               totalTasks;
        private Map<String, Long>  countByStatus;
        private long               overdueCount;
        private List<TaskResponse> recentOverdueTasks;
    }
}