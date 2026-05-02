package com.sitegenius.hrms.dto.response;

import com.sitegenius.hrms.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserResponse {
    private Long id;
    private String fullName;
    private String email;
    private Role role;
    private String phone;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfBirth;
    private String address;
    private BigDecimal monthlySalary;
    private boolean active;
    private boolean laptopAssigned;
    private LocalDateTime createdAt;
    private String employeeCode;
}