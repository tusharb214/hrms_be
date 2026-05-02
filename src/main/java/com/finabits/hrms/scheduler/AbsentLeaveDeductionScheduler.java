package com.sitegenius.hrms.scheduler;

import com.sitegenius.hrms.entity.Attendance;
import com.sitegenius.hrms.entity.Leave;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.AttendanceStatus;
import com.sitegenius.hrms.enums.LeaveStatus;
import com.sitegenius.hrms.enums.LeaveType;
import com.sitegenius.hrms.enums.Role;
import com.sitegenius.hrms.repository.AttendanceRepository;
import com.sitegenius.hrms.repository.LeaveRepository;
import com.sitegenius.hrms.repository.UserRepository;
import com.sitegenius.hrms.repository.LeaveTypeConfigRepository;
import com.sitegenius.hrms.repository.WorkSummaryRepository;
import com.sitegenius.hrms.service.EmailService;
import com.sitegenius.hrms.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Runs at 11:58 PM — AFTER work summary deadline (11:55 PM)
 *
 * Priority order:
 * 1. No work summary → ABSENT (overrides everything)
 * 2. Checked out:
 *    - checkout after 4 PM + mid-slot done → PRESENT
 *    - else → HALF_DAY
 * 3. Not checked out:
 *    - has mid-slot → HALF_DAY
 *    - no mid-slot → ABSENT
 * 4. No check-in at all → ABSENT
 *
 * For ABSENT  → deduct 1 leave (paid first, then unpaid)
 * For HALF_DAY → deduct 0.5 leave (paid first, then unpaid)
 * For PRESENT  → 0 deduction
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbsentLeaveDeductionScheduler {

    private final AttendanceRepository  attendanceRepository;
    private final LeaveRepository       leaveRepository;
    private final UserRepository        userRepository;
    private final WorkSummaryRepository workSummaryRepository;
    private final SystemSettingService  settingService;
    private final EmailService                emailService;
    private final LeaveTypeConfigRepository   leaveTypeConfigRepository;

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
    private static final LocalTime         FULL_CUT  = LocalTime.of(16, 0);

    @Scheduled(cron = "0 58 23 * * MON-SAT")
    public void processDailyAttendance() {
        LocalDate today      = LocalDate.now();
        String    dateLabel  = today.format(DATE_FMT);
        int       year       = today.getYear();
        int       allowedPerYear = settingService.getLeavesPerYear();

        List<String> slots;
        try { slots = settingService.getCheckinTimes(); }
        catch (Exception e) { slots = List.of(); }

        String midSlot = slots.size() >= 2 ? slots.get(1) : null;

        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        log.info("Daily attendance processing started for {}", dateLabel);

        for (User emp : employees) {
            try {
                processEmployee(emp, today, dateLabel, year, allowedPerYear, midSlot);
            } catch (Exception e) {
                log.error("Error processing {} : {}", emp.getEmail(), e.getMessage());
            }
        }

        log.info("Daily attendance processing complete for {}", dateLabel);
    }

    private void processEmployee(User emp, LocalDate today, String dateLabel,
                                 int year, int allowedPerYear, String midSlot) {

        List<Attendance> records = attendanceRepository.findByUserAndDate(emp, today);

        // ── Step 1: Determine final status ────────────────────────────────────
        boolean summarySubmitted = workSummaryRepository
                .findByUserAndSummaryDate(emp, today)
                .map(ws -> ws.isSubmitted()).orElse(false);

        boolean checkedOut = records.stream().anyMatch(Attendance::isCheckedOut);
        boolean checkedIn  = !records.isEmpty();
        boolean hasMidSlot = midSlot != null && records.stream()
                .anyMatch(r -> midSlot.equals(r.getSlotLabel()));

        AttendanceStatus finalStatus;
        String reason;
        double leaveToDeduct;

        if (!summarySubmitted && checkedIn) {
            // No work summary = ABSENT regardless of check-ins
            finalStatus   = AttendanceStatus.ABSENT;
            reason        = "Work summary not submitted";
            leaveToDeduct = 1.0;
        } else if (!checkedIn) {
            finalStatus   = AttendanceStatus.ABSENT;
            reason        = "No check-ins";
            leaveToDeduct = 1.0;
        } else if (checkedOut) {
            // Use status already set during checkout
            AttendanceStatus checkoutStatus = records.stream()
                    .filter(Attendance::isCheckedOut)
                    .map(Attendance::getStatus)
                    .findFirst().orElse(AttendanceStatus.HALF_DAY);
            finalStatus   = checkoutStatus;
            reason        = checkoutStatus == AttendanceStatus.PRESENT ? "Full day" : "Half day checkout";
            leaveToDeduct = checkoutStatus == AttendanceStatus.PRESENT ? 0.0 : 0.5;
        } else {
            // Not checked out
            if (hasMidSlot) {
                finalStatus   = AttendanceStatus.HALF_DAY;
                reason        = "No check-out — mid-slot present";
                leaveToDeduct = 0.5;
            } else {
                finalStatus   = AttendanceStatus.ABSENT;
                reason        = "No mid-slot check-in and no check-out";
                leaveToDeduct = 1.0;
            }
        }

        // ── Step 2: Update attendance records ─────────────────────────────────
        for (Attendance rec : records) {
            rec.setStatus(finalStatus);
            attendanceRepository.save(rec);
        }

        // ── Step 3: Skip deduction if PRESENT ────────────────────────────────
        if (leaveToDeduct == 0.0) {
            log.info("PRESENT: {} on {} — no deduction", emp.getEmail(), today);
            return;
        }

        // ── Step 4: Skip if already on approved manual leave ─────────────────
        boolean onManualLeave = !leaveRepository.findActiveLeaveOnDate(emp, today).isEmpty();
        if (onManualLeave) {
            log.debug("{} on manual leave — leave already applied", emp.getEmail());
            return;
        }

        // Prevent duplicate auto-leave
        if (leaveRepository.existsAutoGeneratedLeaveOnDate(emp, today)) {
            log.debug("{} already has auto-leave for {} — skipping", emp.getEmail(), today);
            return;
        }

        // ── Step 5: Calculate remaining paid leaves ───────────────────────────
        double consumed  = leaveRepository.sumLeaveConsumedByYear(emp, year);
        double remaining = Math.max(0, allowedPerYear - consumed);

        // ── Step 6: Deduct leave (paid first, then unpaid) ────────────────────
        if (remaining >= leaveToDeduct) {
            // Has enough paid leave
            var casualType = leaveTypeConfigRepository.findByTypeCode("CASUAL").orElse(null);
            Leave autoLeave = Leave.builder()
                    .user(emp)
                    .startDate(today).endDate(today)
                    .totalDays(leaveToDeduct == 1.0 ? 1 : 1)
                    .paidDays(leaveToDeduct == 1.0 ? 1 : 0)
                    .unpaidDays(0)
                    .leaveType(LeaveType.PAID)
                    .leaveTypeConfig(casualType)
                    .status(LeaveStatus.APPROVED)
                    .reason("Auto-deducted: " + reason + " on " + dateLabel)
                    .autoGenerated(true)
                    .leaveConsumed(leaveToDeduct)
                    .build();
            leaveRepository.save(autoLeave);

            records.forEach(a -> { a.setSalaryDeductible(false); attendanceRepository.save(a); });

            log.info("{} — {} — {} leave auto-consumed (paid) | remaining: {}→{}",
                    emp.getEmail(), finalStatus, leaveToDeduct, remaining, remaining - leaveToDeduct);

            emailService.sendLeaveAutoConsumed(emp.getEmail(), emp.getFullName(), dateLabel,
                    leaveToDeduct + " day(s)",
                    String.format("%.1f day(s)", remaining - leaveToDeduct),
                    leaveToDeduct == 0.5);

        } else if (remaining > 0 && remaining < leaveToDeduct) {
            // Partial paid, rest unpaid (only possible for half-day scenario)
            double paidPart   = remaining;
            double unpaidPart = leaveToDeduct - remaining;

            var casualTypeMixed = leaveTypeConfigRepository.findByTypeCode("CASUAL").orElse(null);
            Leave autoLeave = Leave.builder()
                    .user(emp).startDate(today).endDate(today)
                    .totalDays(1).paidDays(0).unpaidDays(0)
                    .leaveType(LeaveType.MIXED)
                    .leaveTypeConfig(casualTypeMixed)
                    .status(LeaveStatus.APPROVED)
                    .reason("Auto-deducted (mixed): " + reason + " on " + dateLabel)
                    .autoGenerated(true)
                    .leaveConsumed(leaveToDeduct)
                    .build();
            leaveRepository.save(autoLeave);

            records.forEach(a -> { a.setSalaryDeductible(unpaidPart > 0); attendanceRepository.save(a); });

            log.info("{} — MIXED deduction: {}paid + {}unpaid", emp.getEmail(), paidPart, unpaidPart);

            emailService.sendSalaryDeductionWarning(emp.getEmail(), emp.getFullName(), dateLabel,
                    unpaidPart + "", leaveToDeduct == 0.5);

        } else {
            // No paid leaves left — full unpaid/salary deduction
            records.forEach(a -> { a.setSalaryDeductible(true); attendanceRepository.save(a); });

            log.warn("{} — {} — salary deduction flagged (0 leaves)", emp.getEmail(), finalStatus);

            emailService.sendSalaryDeductionWarning(emp.getEmail(), emp.getFullName(), dateLabel,
                    leaveToDeduct + "", leaveToDeduct == 0.5);
        }
    }
}