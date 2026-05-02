package com.sitegenius.hrms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkSummaryRequest {

    @NotBlank(message = "Summary is required")
    private String summary;

    private String tasksCompleted;   // newline-separated tasks

    private String blockers;         // any blockers today

    private String tomorrowPlan;     // plan for tomorrow

    @Min(value = 1, message = "Mood must be between 1 and 5")
    @Max(value = 5, message = "Mood must be between 1 and 5")
    private Integer moodRating;
}