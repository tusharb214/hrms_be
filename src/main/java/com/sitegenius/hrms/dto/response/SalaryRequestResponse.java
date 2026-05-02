package com.sitegenius.hrms.dto.response;

import com.sitegenius.hrms.enums.Salary;
import lombok.*;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SalaryRequestResponse {
    private Long   id;
    private Long   userId;
    private String employeeCode;
    private String employeeName;
    private String email;
    private int    month;
    private int    year;
    private Salary salary;
    private String rejectionReason;
    private Long   salaryId;           // populated after approval
    private LocalDateTime requestedAt;
    private LocalDateTime updatedAt;
}