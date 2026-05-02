package com.sitegenius.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.sitegenius.hrms.enums.Salary;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_request",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "month", "year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SalaryRequest {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Salary status = Salary.PENDING;

    /** Set when admin rejects, so employee knows why */
    private String rejectionReason;

    /** The generated salary id after approval */
    private Long salaryId;

    @CreationTimestamp
    private LocalDateTime requestedAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}