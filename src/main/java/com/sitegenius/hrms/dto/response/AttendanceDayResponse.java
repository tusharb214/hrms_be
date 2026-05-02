package com.sitegenius.hrms.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceDayResponse {
    private LocalDate    date;
    private String       dayOfWeek;
    private List<SlotEntry> slots;
    private int          checkedIn;
    private int          missed;
    private int          total;
    private String       dayStatus;        // PRESENT / HALF_DAY / ABSENT / IN_PROGRESS / FUTURE
    private String       dayNote;          // Human readable reason e.g. "Worked 6.5h — checked out before 4 PM"
    private boolean      checkedOut;
    private LocalTime    checkoutTime;
    private BigDecimal   totalHours;
    private BigDecimal   leaveDeducted;    // 0 / 0.5 / 1
    private boolean      summarySubmitted;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SlotEntry {
        private String    slotLabel;
        private boolean   checkedIn;
        private LocalTime checkInTime;
        private boolean   workFromHome;
        private String    status;          // PRESENT / MISSED / UPCOMING
    }
}