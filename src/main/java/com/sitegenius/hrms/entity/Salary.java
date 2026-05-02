package com.sitegenius.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "month", "year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Salary {

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

    @Column(precision = 12, scale = 2)
    private BigDecimal grossSalary;

    @Column(precision = 12, scale = 2)
    private BigDecimal perDayRate;

    private int workingDays;
    private int presentDays;
    private int halfDays;               // NEW: days with HALF_DAY attendance status
    private int paidLeaves;
    private int unpaidLeaves;

    @Column(precision = 12, scale = 2)
    private BigDecimal halfDayDeduction;        // NEW: (perDayRate / 2) × halfDays with no leaves

    @Column(precision = 12, scale = 2)
    private BigDecimal unpaidLeaveDeduction;    // NEW: perDayRate × absent/unpaid days with no leaves

    @Column(precision = 12, scale = 2)
    private BigDecimal deductionAmount;         // total = halfDayDeduction + unpaidLeaveDeduction

    @Column(precision = 12, scale = 2)
    private BigDecimal netSalary;

    private String notes;

    @CreationTimestamp
    private LocalDateTime generatedAt;
}