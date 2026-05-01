package com.finabits.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_summaries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "summary_date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate summaryDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    // Tasks completed — stored as newline-separated string, split on frontend
    @Column(columnDefinition = "TEXT")
    private String tasksCompleted;

    // Blockers / challenges faced
    @Column(columnDefinition = "TEXT")
    private String blockers;

    // Plan for tomorrow
    @Column(columnDefinition = "TEXT")
    private String tomorrowPlan;

    // Mood rating 1-5
    private Integer moodRating;

    // Auto-set to true when submitted
    @Builder.Default
    private boolean submitted = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}