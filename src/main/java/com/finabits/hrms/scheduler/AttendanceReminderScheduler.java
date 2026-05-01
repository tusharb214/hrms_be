package com.finabits.hrms.scheduler;

import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.repository.UserRepository;
import com.finabits.hrms.service.EmailService;
import com.finabits.hrms.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceReminderScheduler {

    private final SystemSettingService settingService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Runs every minute. Checks if current time matches any configured slot
     * minus the reminder offset (all from system_settings — zero hardcoding).
     */
    @Scheduled(cron = "0 * * * * MON-SAT")   // every minute Mon-Sat
    public void sendReminders() {
        try {
            List<String> slots        = settingService.getCheckinTimes();
            int reminderMinutesBefore = settingService.getReminderMinutesBefore();

            LocalTime nowTruncated = LocalTime.now().withSecond(0).withNano(0);

            for (String slot : slots) {
                LocalTime slotTime     = LocalTime.parse(slot, TIME_FMT);
                LocalTime reminderTime = slotTime.minusMinutes(reminderMinutesBefore);

                if (nowTruncated.equals(reminderTime)) {
                    log.info("Sending reminders for slot {} ({}min before)", slot, reminderMinutesBefore);
                    dispatchReminders(slot);
                }
            }
        } catch (Exception e) {
            log.error("Reminder scheduler error: {}", e.getMessage());
        }
    }

    private void dispatchReminders(String slotTime) {
        List<User> activeEmployees = userRepository.findByRole(Role.EMPLOYEE)
                .stream()
                .filter(User::isActive)
                .toList();

        int sent = 0;
        for (User employee : activeEmployees) {
            // Pass employeeId so EmailService can skip employees on approved leave today
            emailService.sendCheckinReminder(
                    employee.getEmail(),
                    employee.getFullName(),
                    slotTime,
                    employee.getId()          // ← KEY FIX: enables leave guard
            );
            sent++;
        }
        log.info("Dispatched {} reminder emails for slot {}", sent, slotTime);
    }
}