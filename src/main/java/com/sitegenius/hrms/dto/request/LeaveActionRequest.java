package com.sitegenius.hrms.dto.request;

import com.sitegenius.hrms.enums.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LeaveActionRequest {

    @NotNull(message = "Status is required (APPROVED or REJECTED)")
    private LeaveStatus status;

    private String adminComment;
}
