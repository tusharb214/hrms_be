package com.sitegenius.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * TaskMember — join entity between Task and User.
 *
 * One Task can have many members.
 * Admin can add/remove members at any time.
 * All members share the same task status.
 */
@Entity
@Table(
        name = "task_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_member",
                columnNames = {"task_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_task_member_task_id",  columnList = "task_id"),
                @Index(name = "idx_task_member_user_id",  columnList = "user_id")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Who added this member (admin) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_id")
    private User addedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;
}