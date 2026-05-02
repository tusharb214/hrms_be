package com.sitegenius.hrms.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkSummaryResponse {
    private Long id;
    private Long userId;
    private String employeeName;
    private LocalDate summaryDate;
    private String summary;
    private String tasksCompleted;
    private String blockers;
    private String tomorrowPlan;
    private Integer moodRating;
    private boolean submitted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}