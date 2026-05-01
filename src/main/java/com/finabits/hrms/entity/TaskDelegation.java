
package com.finabits.hrms.entity;

import jakarta.persistence.*;
        import lombok.*;
        import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Records every hop in a task's delegation chain.
 *
 * A row is inserted whenever:
 *   - An employee delegates  → type = DELEGATED
 *   - A delegatee returns    → type = RETURNED
 *
 * The full chain can be reconstructed by querying
 *   findByTaskIdOrderByCreatedAtAsc(taskId)
 */
@Entity
@Table(name = "task_delegations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskDelegation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The task this delegation belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /** Who sent the task forward / back */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    /** Who received the task */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    /**
     * DELEGATED  — fromUser forwarded work to toUser
     * RETURNED   — toUser's chain finished, task is back with fromUser
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DelegationType type;

    /** Optional note explaining why the task is being delegated / returned */
    @Column(columnDefinition = "TEXT")
    private String note;

    /** Hop number in the chain (1 = first delegation, 2 = second, …) */
    @Column(nullable = false)
    private int hopNumber;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum DelegationType {
        DELEGATED,   // task forwarded to someone else
        RETURNED     // task came back after delegatee submitted it
    }
}