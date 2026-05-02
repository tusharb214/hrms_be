package com.sitegenius.hrms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private Long          id;
    private String        title;
    private String        content;
    private boolean       active;
    private LocalDateTime createdAt;

    // Only safe fields from createdBy — no password, no sensitive data
    private Long   createdById;
    private String createdByName;
}