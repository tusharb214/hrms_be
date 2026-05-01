package com.finabits.hrms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_type_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaveTypeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String typeName;        // e.g. "Sick Leave", "Casual Leave", "Earned Leave"

    @Column(nullable = false)
    private String typeCode;        // e.g. "SICK", "CASUAL", "EARNED"

    private int allowedPerYear;     // how many days per year for this type

    private boolean halfDayAllowed; // can employee apply half day?

    @Builder.Default
    private boolean active = true;

    private String description;

    @Builder.Default
    private boolean requiresDocument = false; // e.g. sick leave needs medical cert

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}