package com.sitegenius.hrms.scheduler;

import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.Role;
import com.sitegenius.hrms.repository.AttendanceRepository;
import com.sitegenius.hrms.repository.UserRepository;
import com.sitegenius.hrms.service.NotificationService;
import com.sitegenius.hrms.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class MissedCheckinNotificationScheduler {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository       userRepository;
    private final SystemSettingService settingService;
    private final NotificationService  notificationService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int SCAN_WINDOW_MINS = 6; // slightly more than cron interval

    /**
     * Runs every 5 minutes on working days.
     * Checks each slot — if window closed in last 6 minutes, fire notifications
     * to active employees who did NOT check in for that slot.
     */
    @Scheduled(cron = "0 */5 * * * MON-SAT")
    public void checkMissedCheckins() {
        LocalDate today = LocalDate.now();
        LocalTime now   = LocalTime.now();

        List<String> slots;
        int windowMins;
        try {
            slots      = settingService.getCheckinTimes();
            windowMins = settingService.getIntValue("checkin_window_minutes");
        } catch (Exception e) {
            log.debug("MissedCheckin scheduler: could not load slots — skipping");
            return;
        }

        if (slots == null || slots.isEmpty()) return;

        List<User> activeEmployees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        if (activeEmployees.isEmpty()) return;

        for (String slot : slots) {
            try {
                LocalTime slotTime  = LocalTime.parse(slot, TIME_FMT);
                LocalTime windowEnd = slotTime.plusMinutes(windowMins);

                // Only fire if the window JUST closed (within last SCAN_WINDOW_MINS minutes)
                boolean justClosed = !now.isBefore(windowEnd)
                        && now.isBefore(windowEnd.plusMinutes(SCAN_WINDOW_MINS));

                if (!justClosed) continue;

                // Find employees who did NOT check in for this slot today
                for (User emp : activeEmployees) {
                    boolean checkedIn = attendanceRepository
                            .existsByUserAndDateAndSlotLabel(emp, today, slot);
                    if (!checkedIn) {
                        notificationService.missedCheckin(
                                emp.getId(),
                                emp.getFullName(),
                                slot,
                                today.toString());
                    }
                }
                log.info("MissedCheckin notifications fired for slot {} on {}", slot, today);

            } catch (Exception e) {
                log.error("Error processing missed checkin for slot {}: {}", slot, e.getMessage());
            }
        }
    }
}