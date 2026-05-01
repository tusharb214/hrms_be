package com.finabits.hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AttendanceRequest {

    @NotBlank(message = "Slot label is required (e.g. 10:00)")
    private String slotLabel;

    private boolean workFromHome;
}
