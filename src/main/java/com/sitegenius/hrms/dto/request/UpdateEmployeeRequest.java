package com.sitegenius.hrms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateEmployeeRequest {
    private String fullName;

    @Email(message = "Invalid email format")
    private String email;

    private String phone;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfBirth;
    private String address;

    @DecimalMin(value = "0.0", inclusive = false, message = "Salary must be positive")
    private BigDecimal monthlySalary;
    private Boolean active;
    private Boolean laptopAssigned;
}