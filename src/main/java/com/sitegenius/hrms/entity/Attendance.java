package com.sitegenius.hrms.entity;

import com.sitegenius.hrms.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "date", "slot_label"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String slotLabel;   // e.g. "10:00", "14:00", "CHECKOUT"

    private LocalTime checkInTime;

    // ── Checkout fields ───────────────────────────────────────────────────────
    private LocalTime checkoutTime;       // set when employee checks out

    @Column(precision = 5, scale = 2)
    private BigDecimal totalHours;        // checkoutTime - first checkInTime

    @Builder.Default
    private boolean checkedOut = false;   // true once checkout done

    @Builder.Default
    private boolean salaryDeductible = false; // true if no leave remaining

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    private boolean workFromHome;

    @CreationTimestamp
    private LocalDateTime createdAt;
}