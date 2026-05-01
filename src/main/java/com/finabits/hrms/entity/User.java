package com.finabits.hrms.entity;

import com.finabits.hrms.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable employee ID, e.g. "FIN-1001".
     * Auto-assigned on first save via EmployeeCodeService.
     * Unique and non-null after migration.
     */
    @Column(unique = true)
    private String employeeCode;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String phone;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfBirth;
    private String address;

    @Column(precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    private boolean laptopAssigned = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}