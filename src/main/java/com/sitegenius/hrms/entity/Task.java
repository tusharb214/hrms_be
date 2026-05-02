package com.sitegenius.hrms.entity;

import com.sitegenius.hrms.enums.Priority;
import com.sitegenius.hrms.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Task entity — multi-member shared task model.
 *
 * KEY CHANGES:
 *   - assignedTo is now nullable=true (no legacy 'assigned_to' plain column needed)
 *     Run: ALTER TABLE tasks DROP COLUMN assigned_to;  on your live DB before deploying.
 *   - resolvedBy  : the employee who submitted the task (moved to SUBMITTED)
 *   - resolvedAt  : when it was submitted
 *   - approvedBy  : the admin who APPROVED or REJECTED
 *   - approvedAt  : when the review decision was made
 *   - reviewNote  : admin's review comment stored on task directly for quick display
 */
@Entity
@Table(name = "tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private LocalDate deadline;

    /**
     * Primary assignee — the first/lead person admin assigned.
     * nullable=true so Hibernate only writes assigned_to_id FK column.
     * Run: ALTER TABLE tasks DROP COLUMN assigned_to;  on live DB.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id", nullable = true)
    private User assignedTo;

    /** Admin who created the task */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    // ── Resolution tracking ───────────────────────────────────────────────────

    /**
     * The employee who submitted/resolved this task (set when status → SUBMITTED).
     * This tells "who resolved / completed" the task.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    /** When the task was submitted (resolved) by the employee */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /**
     * The admin who approved or rejected the task (set during review).
     * This tells "who reviewed / approved" the task.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    /** When the admin reviewed (approved/rejected) */
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Final review note from admin (stored directly for quick display) */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /**
     * All members assigned to this task.
     * Loaded lazily — fetch explicitly when needed.
     */
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TaskMember> members = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}