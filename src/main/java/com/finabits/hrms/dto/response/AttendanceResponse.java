package com.sitegenius.hrms.dto.response;

import com.sitegenius.hrms.enums.AttendanceStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceResponse {
    private Long             id;
    private Long             userId;
    private String           employeeName;
    private LocalDate        date;
    private String           slotLabel;
    private LocalTime        checkInTime;
    private LocalTime        checkoutTime;
    private BigDecimal       totalHours;
    private boolean          checkedOut;
    private AttendanceStatus status;
    private boolean          workFromHome;
    private boolean          salaryDeductible;
}