package com.sitegenius.hrms.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SalaryResponse {
    private Long   id;
    private Long   userId;
    private String employeeCode;          // NEW — e.g. "FIN-1001"
    private String employeeName;
    private int    month;
    private int    year;
    private BigDecimal grossSalary;
    private BigDecimal perDayRate;
    private int    workingDays;
    private int    presentDays;
    private int    halfDays;
    private int    paidLeaves;
    private Integer unpaidLeaves;
    private BigDecimal halfDayDeduction;
    private BigDecimal unpaidLeaveDeduction;
    private BigDecimal deductionAmount;
    private BigDecimal netSalary;
    private String notes;
    private LocalDateTime generatedAt;
}