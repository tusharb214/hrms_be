package com.finabits.hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class LeaveRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Reason is required")
    private String reason;

    // Dynamic leave type — references leave_type_configs.id
    private Long leaveTypeConfigId;

    // Half day support
    private boolean halfDay = false;
    private String  halfDaySlot; // "MORNING" (10AM-2PM) or "AFTERNOON" (2PM-6PM)

    // Supporting document (optional — uploaded separately via multipart)
    private String documentPath;
    private String documentName;
}