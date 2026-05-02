package com.sitegenius.hrms.service;

import com.sitegenius.hrms.dto.request.WorkSummaryRequest;
import com.sitegenius.hrms.dto.response.WorkSummaryResponse;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.entity.WorkSummary;
import com.sitegenius.hrms.exception.BadRequestException;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.repository.AttendanceRepository;
import com.sitegenius.hrms.repository.UserRepository;
import com.sitegenius.hrms.repository.WorkSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkSummaryService {

    private final WorkSummaryRepository summaryRepository;
    private final UserRepository        userRepository;
    private final AttendanceRepository  attendanceRepository;
    private final SystemSettingService  settingService;
    private final EmailService          emailService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");

    // ── Submit today's summary ────────────────────────────────────────────────
    public WorkSummaryResponse submitSummary(WorkSummaryRequest request) {
        User user = getCurrentUser();
        LocalDate today = LocalDate.now();

        if (LocalTime.now().isAfter(LocalTime.of(23, 55)))
            throw new BadRequestException("Submission closed for today. Deadline is 11:55 PM.");

        WorkSummary summary = summaryRepository.findByUserAndSummaryDate(user, today)
                .orElse(WorkSummary.builder().user(user).summaryDate(today).build());

        summary.setSummary(request.getSummary());
        summary.setTasksCompleted(request.getTasksCompleted());
        summary.setBlockers(request.getBlockers());
        summary.setTomorrowPlan(request.getTomorrowPlan());
        summary.setMoodRating(request.getMoodRating());
        summary.setSubmitted(true);

        summaryRepository.save(summary);
        log.info("Work summary submitted by {} for {}", user.getEmail(), today);
        return mapToResponse(summary);
    }

    // ── Get today's summary ───────────────────────────────────────────────────
    public WorkSummaryResponse getTodaySummary() {
        User user = getCurrentUser();
        return summaryRepository.findByUserAndSummaryDate(user, LocalDate.now())
                .map(this::mapToResponse).orElse(null);
    }

    // ── My history with optional filters ─────────────────────────────────────
    public Page<WorkSummaryResponse> getMyHistory(Pageable pageable,
                                                  Integer month, Integer year, LocalDate date) {
        User user = getCurrentUser();
        if (date != null)
            return summaryRepository
                    .findByUserAndSummaryDateOrderBySummaryDateDesc(user, date, pageable)
                    .map(this::mapToResponse);
        if (month != null && year != null)
            return summaryRepository
                    .findByUserAndMonthYear(user, month, year, pageable)
                    .map(this::mapToResponse);
        return summaryRepository
                .findByUserOrderBySummaryDateDesc(user, pageable)
                .map(this::mapToResponse);
    }

    // ── Admin: specific employee summaries with filters ───────────────────────
    public Page<WorkSummaryResponse> getEmployeeSummaries(Long userId, Pageable pageable,
                                                          Integer month, Integer year, LocalDate date) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        if (date != null)
            return summaryRepository.findByUserIdAndDate(userId, date, pageable).map(this::mapToResponse);
        if (month != null && year != null)
            return summaryRepository.findByUserIdAndMonthYear(userId, month, year, pageable).map(this::mapToResponse);
        return summaryRepository.findByUserId(userId, pageable).map(this::mapToResponse);
    }

    // ── Admin: get specific date summary ─────────────────────────────────────
    public WorkSummaryResponse getSummaryByEmployeeAndDate(Long userId, LocalDate date) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return summaryRepository.findByUserAndSummaryDate(user, date)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No summary found for this date"));
    }

    // ── Missing submissions today ─────────────────────────────────────────────
    public List<String> getMissingSubmissionsToday() {
        return summaryRepository.findEmployeesWithoutSummaryOnDate(LocalDate.now())
                .stream().map(User::getFullName).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SCHEDULERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 6 PM — First reminder to employees who haven't submitted yet.
     */
    @Scheduled(cron = "0 0 18 * * MON-SAT")
    public void sendReminder6PM() {
        sendReminderToMissing("6:00 PM", "first",
                "Don't forget to submit your work summary.",
                "You have until <strong>11:55 PM</strong> to submit.");
    }

    /**
     * 8 PM — Second reminder.
     */
    @Scheduled(cron = "0 0 20 * * MON-SAT")
    public void sendReminder8PM() {
        sendReminderToMissing("8:00 PM", "second",
                "You haven't submitted your work summary yet.",
                "⚠️ Only 4 hours left! Not submitting = <strong>ABSENT + leave deducted</strong>.");
    }

    /**
     * 10 PM — Final reminder.
     */
    @Scheduled(cron = "0 0 22 * * MON-SAT")
    public void sendReminder10PM() {
        sendReminderToMissing("10:00 PM", "final",
                "🚨 FINAL REMINDER — Submit your work summary NOW!",
                "⛔ Less than 2 hours left! No summary = <strong>ABSENT + 1 leave deducted</strong>.");
    }

    /**
     * Core reminder — only sends to employees who:
     * 1. Checked in today
     * 2. Have NOT submitted summary
     */
    private void sendReminderToMissing(String time, String num, String headline, String note) {
        LocalDate today     = LocalDate.now();
        String    dateLabel = today.format(DATE_FMT);

        List<User> missing = summaryRepository.findEmployeesWithoutSummaryOnDate(today);
        if (missing.isEmpty()) {
            log.info("Work summary {} reminder: everyone submitted ✅", num);
            return;
        }

        int sent = 0;
        for (User emp : missing) {
            // Only remind those who checked in (not absent/on leave)
            if (!attendanceRepository.findByUserAndDate(emp, today).isEmpty()) {
                emailService.sendWorkSummaryReminder(emp.getEmail(), emp.getFullName(),
                        dateLabel, time, headline, note);
                sent++;
            }
        }
        log.info("Work summary {} reminder ({}): sent to {}", num, time, sent);
    }

    // NOTE: Marking ABSENT + leave deduction for missing summaries is handled
    // entirely by AbsentLeaveDeductionScheduler at 11:58 PM — no duplication here.

    // ── Helpers ───────────────────────────────────────────────────────────────
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private WorkSummaryResponse mapToResponse(WorkSummary ws) {
        return WorkSummaryResponse.builder()
                .id(ws.getId())
                .userId(ws.getUser().getId())
                .employeeName(ws.getUser().getFullName())
                .summaryDate(ws.getSummaryDate())
                .summary(ws.getSummary())
                .tasksCompleted(ws.getTasksCompleted())
                .blockers(ws.getBlockers())
                .tomorrowPlan(ws.getTomorrowPlan())
                .moodRating(ws.getMoodRating())
                .submitted(ws.isSubmitted())
                .createdAt(ws.getCreatedAt())
                .updatedAt(ws.getUpdatedAt())
                .build();
    }
}