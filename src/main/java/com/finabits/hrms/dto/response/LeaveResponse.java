package com.finabits.hrms.dto.response;

import com.finabits.hrms.enums.LeaveStatus;
import com.finabits.hrms.enums.LeaveType;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveResponse {
    private Long          id;
    private Long          userId;
    private String        employeeName;
    // ✅ ADD THIS
    private Long leaveTypeConfigId;
    private LocalDate     startDate;
    private LocalDate     endDate;
    private int           totalDays;
    private int           paidDays;
    private int           unpaidDays;
    private boolean       halfDay;
    private Double        leaveConsumed; // actual deduction — 0.5 for half day, 1.0 for full
    private String        halfDaySlot;   // MORNING / AFTERNOON
    private LeaveType     leaveType;       // PAID / UNPAID / MIXED
    private String        leaveTypeName;   // e.g. "Sick Leave", "Casual Leave"
    private String        leaveTypeCode;   // e.g. "SICK", "CASUAL"
    private LeaveStatus   status;
    private String        reason;
    private String        adminComment;
    private String        approvedByName;
    private String        documentName;    // supporting doc filename
    private boolean       hasDocument;
    private LocalDateTime appliedAt;
    private LocalDateTime actionAt;
}