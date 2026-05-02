package com.sitegenius.hrms.dto.request;

import com.sitegenius.hrms.enums.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    private String phone;
    private String department;
    private String designation;
    private LocalDate dateOfJoining;
    private LocalDate dateOfBirth;
    private String address;

    @DecimalMin(value = "0.0", inclusive = false, message = "Salary must be positive")
    private BigDecimal monthlySalary;

    @NotNull(message = "Role is required")
    private Role role;
}
