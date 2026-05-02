package com.sitegenius.hrms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class HolidayRequest {

    @NotBlank(message = "Holiday name is required")
    private String name;

    // Single date mode
    private LocalDate date;

    // Range mode — if fromDate + toDate are set, date is ignored
    private LocalDate fromDate;
    private LocalDate toDate;

    private String description;
    private boolean optional;
}