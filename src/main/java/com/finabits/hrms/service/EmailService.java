package com.finabits.hrms.service;

import com.finabits.hrms.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender  mailSender;
    private final LeaveRepository leaveRepository;   // NEW — for leave-day guard

    private static final String LOGO_URL =
            "https://github.com/sitegenius-dev/logo.git";

    // ── Core HTML sender ──────────────────────────────────────────────────────
    @Async
    public void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent → {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    // ── HTML wrapper ──────────────────────────────────────────────────────────
    private String wrap(String accentColor, String icon, String title, String bodyContent) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>" +
                "body{margin:0;padding:0;background:#F0F3F9;font-family:'Segoe UI',Arial,sans-serif;}" +
                ".wrapper{max-width:580px;margin:32px auto;background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 24px rgba(28,52,97,.12);}" +
                ".header{background:#1C3461;padding:20px 32px;}" +
                ".logo-img{height:40px;width:auto;display:block;}" +
                ".accent-bar{height:4px;background:" + accentColor + ";}" +
                ".body{padding:32px;}" +
                ".icon-wrap{width:54px;height:54px;border-radius:50%;background:" + accentColor + "20;display:flex;align-items:center;justify-content:center;font-size:26px;margin-bottom:16px;}" +
                ".title{font-size:20px;font-weight:800;color:#1C3461;margin-bottom:8px;}" +
                "hr.divider{border:none;border-top:1px solid #E2E8F4;margin:18px 0;}" +
                ".footer{background:#F7F9FD;padding:16px 32px;text-align:center;font-size:11px;color:#9AA8BF;border-top:1px solid #E2E8F4;}" +
                ".footer img{height:22px;width:auto;opacity:.6;margin-bottom:6px;display:block;margin-left:auto;margin-right:auto;}" +
                "p{font-size:13.5px;color:#374151;line-height:1.7;margin:8px 0;}" +
                ".highlight{background:" + accentColor + "15;border-left:3px solid " + accentColor + ";padding:10px 16px;border-radius:0 8px 8px 0;margin:14px 0;}" +
                ".highlight p{margin:0;font-weight:600;}" +
                "table.info{width:100%;border-collapse:collapse;margin:14px 0;}" +
                "table.info td{padding:9px 12px;font-size:13px;border-bottom:1px solid #EEF2FA;}" +
                "table.info td:first-child{color:#6B7A99;font-weight:600;width:42%;}" +
                "table.info td:last-child{color:#1C3461;font-weight:700;}" +
                ".badge{display:inline-block;padding:3px 10px;border-radius:20px;font-size:11px;font-weight:700;}" +
                ".badge-low{background:#DCFCE7;color:#15803D;}" +
                ".badge-medium{background:#FEF9C3;color:#B45309;}" +
                ".badge-high{background:#FEE2E2;color:#C0392B;}" +
                "</style></head><body>" +
                "<div class='wrapper'>" +
                "<div class='header'><img src='" + LOGO_URL + "' alt='Sitegenius' class='logo-img'/></div>" +
                "<div class='accent-bar'></div>" +
                "<div class='body'>" +
                "<div class='icon-wrap'>" + icon + "</div>" +
                "<div class='title'>" + title + "</div>" +
                bodyContent +
                "</div>" +
                "<div class='footer'>" +
                "<img src='" + LOGO_URL + "' alt='Sitegenius'/>" +
                "© Sitegenius HRMS &nbsp;·&nbsp; Automated notification &nbsp;·&nbsp; Do not reply" +
                "</div></div></body></html>";
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEAVE-DAY GUARD (email equivalent of NotificationService.isOnApprovedLeaveToday)
    //
    // Called at the top of every attendance-related email method.
    // If the employee is on approved leave today, the email is skipped silently.
    //
    // Does NOT guard: task emails, leave status emails, announcements,
    // holidays, password reset — those are always sent.
    // ════════════════════════════════════════════════════════════════════════

    /**
     * @param employeeId the employee's user ID
     * @return true if this employee has an APPROVED leave covering today
     */
    private boolean isOnApprovedLeaveToday(Long employeeId) {
        if (employeeId == null) {
            log.warn("isOnApprovedLeaveToday called with null employeeId — leave guard cannot run. " +
                    "Find the call site and pass the correct employeeId.");
            return false;   // safe fallback: send the email rather than silently drop it
        }
        try {
            return leaveRepository.isOnApprovedLeaveOnDate(employeeId, LocalDate.now());
        } catch (Exception e) {
            log.warn("Leave check failed for email guard (userId={}): {}", employeeId, e.getMessage());
            return false;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TASK EMAILS  (never suppressed — always sent)
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendTaskAssignedEmail(String to, String employeeName,
                                      String taskTitle, String description,
                                      String priority, String deadline,
                                      String assignedByName) {
        String subject = "📋 New Task Assigned: " + taskTitle;
        String priorityBadge = switch (priority.toUpperCase()) {
            case "HIGH"   -> "<span class='badge badge-high'>🔴 HIGH</span>";
            case "MEDIUM" -> "<span class='badge badge-medium'>🟡 MEDIUM</span>";
            default       -> "<span class='badge badge-low'>🟢 LOW</span>";
        };
        String descRow = (description != null && !description.isBlank())
                ? "<tr><td>Description</td><td>" + description + "</td></tr>" : "";
        String body = wrap("#2471A3", "📋", "New Task Assigned to You",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>You have been assigned a new task by <strong>" + assignedByName + "</strong>.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Task</td><td><strong>" + taskTitle + "</strong></td></tr>" +
                        descRow +
                        "<tr><td>Priority</td><td>" + priorityBadge + "</td></tr>" +
                        "<tr><td>Deadline</td><td><strong style='color:#C0392B;'>" + deadline + "</strong></td></tr>" +
                        "<tr><td>Assigned By</td><td>" + assignedByName + "</td></tr>" +
                        "<tr><td>Status</td><td><span style='color:#2471A3;font-weight:700;'>PENDING</span></td></tr>" +
                        "</table>" +
                        "<div class='highlight'><p>⏰ Please begin working before the deadline.</p></div>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;" +
                        "background:#1C3461;color:#fff;border-radius:10px;text-decoration:none;" +
                        "font-weight:800;font-size:13px;'>View Task on HRMS →</a></p>" +
                        "<hr class='divider'/>" +
                        "<p style='font-size:12px;color:#9AA8BF;'>Update the task status as you progress.</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendTaskOverdueEmail(String to, String employeeName,
                                     String taskTitle, String deadline,
                                     String priority) {
        String subject = "⚠️ Task Overdue: " + taskTitle;
        String priorityBadge = switch (priority.toUpperCase()) {
            case "HIGH"   -> "<span class='badge badge-high'>🔴 HIGH</span>";
            case "MEDIUM" -> "<span class='badge badge-medium'>🟡 MEDIUM</span>";
            default       -> "<span class='badge badge-low'>🟢 LOW</span>";
        };
        String body = wrap("#C0392B", "⚠️", "Task Overdue — Immediate Action Required",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>Your task <strong>" + taskTitle +
                        "</strong> has passed its deadline and is now marked <span style='color:#C0392B;'>OVERDUE</span>.</p></div>" +
                        "<table class='info'>" +
                        "<tr><td>Task</td><td><strong>" + taskTitle + "</strong></td></tr>" +
                        "<tr><td>Priority</td><td>" + priorityBadge + "</td></tr>" +
                        "<tr><td>Deadline</td><td><strong style='color:#C0392B;'>" + deadline + "</strong></td></tr>" +
                        "<tr><td>Status</td><td><span style='color:#C0392B;font-weight:800;'>OVERDUE</span></td></tr>" +
                        "</table>" +
                        "<p style='color:#C0392B;font-weight:600;'>Please submit this task as soon as possible.</p>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;" +
                        "background:#C0392B;color:#fff;border-radius:10px;text-decoration:none;" +
                        "font-weight:800;font-size:13px;'>Submit Task Now →</a></p>" +
                        "<hr class='divider'/>" +
                        "<p style='font-size:12px;color:#9AA8BF;'>Contact your manager if you need an extension.</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendTaskReviewedEmail(String to, String employeeName,
                                      String taskTitle, String status, String adminComment) {
        boolean approved   = "APPROVED".equalsIgnoreCase(status);
        String accentColor = approved ? "#15803D" : "#C0392B";
        String icon        = approved ? "✅" : "❌";
        String subject     = (approved ? "✅ Task Approved: " : "❌ Task Rejected: ") + taskTitle;
        String commentRow  = (adminComment != null && !adminComment.isBlank())
                ? "<tr><td>Admin Note</td><td style='color:" + accentColor + ";'>" + adminComment + "</td></tr>" : "";
        String actionNote  = approved
                ? "<p style='color:#15803D;font-weight:600;'>Great work! Your task has been marked complete.</p>"
                : "<p style='color:#C0392B;font-weight:600;'>Please review the feedback, make corrections, and resubmit.</p>" +
                "<p style='text-align:center;margin:20px 0;'><a href='https://hrms-fe-ten.vercel.app' " +
                "style='display:inline-block;padding:12px 28px;background:#C0392B;color:#fff;border-radius:10px;" +
                "text-decoration:none;font-weight:800;font-size:13px;'>Rework Task →</a></p>";
        String body = wrap(accentColor, icon, approved ? "Task Approved" : "Task Rejected — Action Required",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your task has been reviewed.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Task</td><td><strong>" + taskTitle + "</strong></td></tr>" +
                        "<tr><td>Decision</td><td><strong style='color:" + accentColor + ";'>" + status + "</strong></td></tr>" +
                        commentRow + "</table>" + actionNote +
                        "<hr class='divider'/>" +
                        "<p style='font-size:12px;color:#9AA8BF;'>Log in to  Sitegenius HRMS to view full task history.</p>"
        );
        sendEmail(to, subject, body);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ATTENDANCE EMAILS  — GUARDED: skip if employee is on approved leave
    //
    // Each method accepts an optional Long employeeId parameter.
    // If employeeId is null (e.g. for legacy call sites), the guard is skipped
    // and the email is sent (safe fallback).
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ── 1. Check-in Reminder ────────────────────────────────────────────────
     * SKIP if employee is on approved leave today.
     *
     * @param employeeId pass the employee's userId so the leave check works.
     *                   If your existing call site doesn't have it, pass null
     *                   and the email will be sent (safe fallback).
     */
    @Async
    public void sendCheckinReminder(String to, String employeeName,
                                    String slotTime, Long employeeId) {
        if (isOnApprovedLeaveToday(employeeId)) {
            log.info("Skipping check-in reminder email for {} (userId={}) — on approved leave", employeeName, employeeId);
            return;
        }
        String subject = "⏰ Check-in Reminder — Slot opens at " + slotTime;
        String body = wrap("#2471A3", "⏰", "Check-in Reminder",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your next check-in slot opens in a few minutes.</p>" +
                        "<div class='highlight'><p>📍 Slot Time: <strong>" + slotTime + "</strong></p></div>" +
                        "<p>Please log in to Sitegenius HRMS and mark your attendance <strong>at exactly " + slotTime + "</strong>.</p>" +
                        "<p style='color:#C0392B;font-weight:600;font-size:12px;'>⚠️ Missing this slot will affect your attendance record.</p>"
        );
        sendEmail(to, subject, body);
    }

    /**
     * @deprecated Pass {@code employeeId} so the leave guard is applied.
     *             This overload skips the guard — do NOT use for new call sites.
     */
    @Deprecated
    @Async
    public void sendCheckinReminder(String to, String employeeName, String slotTime) {
        log.warn("sendCheckinReminder called WITHOUT employeeId for {} <{}> — leave guard skipped. " +
                "Update the call site to pass employeeId.", employeeName, to);
        sendCheckinReminder(to, employeeName, slotTime, null);
    }

    /**
     * ── 3. Absent — No Summary ──────────────────────────────────────────────
     * SKIP if employee is on approved leave on that date.
     */
    @Async
    public void sendAbsentDueToNoSummary(String to, String employeeName,
                                         String date, Long employeeId) {
        if (isOnApprovedLeaveToday(employeeId)) {
            log.info("Skipping absent-no-summary email for {} (userId={}) — on approved leave on {}", employeeName, employeeId, date);
            return;
        }
        String subject = "⚠️ Attendance Marked Absent — Work Summary Missing";
        String body = wrap("#C0392B", "⚠️", "Attendance Marked Absent",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>Your attendance for <strong>" + date + "</strong> has been marked <span style='color:#C0392B;'>ABSENT</span>.</p></div>" +
                        "<p><strong>Reason:</strong> Work summary not submitted before 11:55 PM deadline.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Date</td><td>" + date + "</td></tr>" +
                        "<tr><td>Status</td><td style='color:#C0392B;font-weight:800;'>ABSENT</td></tr>" +
                        "<tr><td>Reason</td><td>Work summary not submitted</td></tr>" +
                        "</table>" +
                        "<p>Submit your daily work summary on Sitegenius HRMS <strong>before 11:55 PM</strong> every working day to avoid this.</p>"
        );
        sendEmail(to, subject, body);
    }

    /**
     * @deprecated Pass {@code employeeId} so the leave guard is applied.
     *             This overload skips the guard — do NOT use for new call sites.
     */
    @Deprecated
    @Async
    public void sendAbsentDueToNoSummary(String to, String employeeName, String date) {
        log.warn("sendAbsentDueToNoSummary called WITHOUT employeeId for {} <{}> — leave guard skipped. " +
                "Update the call site to pass employeeId.", employeeName, to);
        sendAbsentDueToNoSummary(to, employeeName, date, null);
    }

    /**
     * ── 5a. Work Summary Reminder (simple) ─────────────────────────────────
     * SKIP if employee is on approved leave today.
     */
    @Async
    public void sendWorkSummaryReminder(String to, String employeeName,
                                        String date, Long employeeId) {
        sendWorkSummaryReminder(to, employeeName, date, "6:00 PM",
                "Don't forget to submit your work summary.",
                "You have until <strong>11:55 PM</strong> to submit.", employeeId);
    }

    /**
     * @deprecated Pass {@code employeeId} so the leave guard is applied.
     *             This overload skips the guard — do NOT use for new call sites.
     */
    @Deprecated
    @Async
    public void sendWorkSummaryReminder(String to, String employeeName, String date) {
        log.warn("sendWorkSummaryReminder called WITHOUT employeeId for {} <{}> — leave guard skipped. " +
                "Update the call site to pass employeeId.", employeeName, to);
        sendWorkSummaryReminder(to, employeeName, date, null);
    }

    /**
     * ── 5b. Work Summary Reminder (smart — with time + urgency) ────────────
     * SKIP if employee is on approved leave today.
     */
    @Async
    public void sendWorkSummaryReminder(String to, String employeeName, String date,
                                        String time, String headline, String urgencyNote,
                                        Long employeeId) {
        if (isOnApprovedLeaveToday(employeeId)) {
            log.info("Skipping work-summary reminder email for {} (userId={}) — on approved leave", employeeName, employeeId);
            return;
        }
        String subject = "📝 Work Summary Reminder (" + time + ") — " + date;
        String body = wrap("#1C3461", "📝", "Work Summary Reminder",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>" + headline + "</p></div>" +
                        "<table class='info'>" +
                        "<tr><td>Date</td><td>" + date + "</td></tr>" +
                        "<tr><td>Reminder</td><td>" + time + "</td></tr>" +
                        "<tr><td>Deadline</td><td><strong>11:55 PM tonight</strong></td></tr>" +
                        "</table>" +
                        "<p>" + urgencyNote + "</p>" +
                        "<p>Please fill in:</p>" +
                        "<ul style='font-size:13px;color:#374151;line-height:2;padding-left:20px;'>" +
                        "<li>📋 What you worked on today</li>" +
                        "<li>✅ Tasks completed</li>" +
                        "<li>🚧 Any blockers</li>" +
                        "<li>🔭 Plan for tomorrow</li>" +
                        "</ul>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;background:#1C3461;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:13px;'>Submit Summary →</a>" +
                        "</p>"
        );
        sendEmail(to, subject, body);
    }

    /**
     * @deprecated Pass {@code employeeId} so the leave guard is applied.
     *             This overload skips the guard — do NOT use for new call sites.
     */
    @Deprecated
    @Async
    public void sendWorkSummaryReminder(String to, String employeeName, String date,
                                        String time, String headline, String urgencyNote) {
        log.warn("sendWorkSummaryReminder called WITHOUT employeeId for {} <{}> — leave guard skipped. " +
                "Update the call site to pass employeeId.", employeeName, to);
        sendWorkSummaryReminder(to, employeeName, date, time, headline, urgencyNote, null);
    }

    /**
     * ── 10. Checkout Reminder ───────────────────────────────────────────────
     * SKIP if employee is on approved leave today.
     */
    @Async
    public void sendCheckoutReminder(String to, String employeeName, String date,
                                     String time, String headline, String note,
                                     Long employeeId) {
        if (isOnApprovedLeaveToday(employeeId)) {
            log.info("Skipping checkout reminder email for {} (userId={}) — on approved leave", employeeName, employeeId);
            return;
        }
        String subject = "🚪 Check-Out Reminder (" + time + ") — " + date;
        String body = wrap("#F59E0B", "🚪", "Check-Out Reminder",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>" + headline + "</p></div>" +
                        "<table class='info'>" +
                        "<tr><td>Date</td><td>" + date + "</td></tr>" +
                        "<tr><td>Reminder</td><td>" + time + "</td></tr>" +
                        "</table>" +
                        "<p>" + note + "</p>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;background:#F59E0B;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:13px;'>Check Out Now →</a>" +
                        "</p>"
        );
        sendEmail(to, subject, body);
    }

    /**
     * @deprecated Pass {@code employeeId} so the leave guard is applied.
     *             This overload skips the guard — do NOT use for new call sites.
     */
    @Deprecated
    @Async
    public void sendCheckoutReminder(String to, String employeeName, String date,
                                     String time, String headline, String note) {
        log.warn("sendCheckoutReminder called WITHOUT employeeId for {} <{}> — leave guard skipped. " +
                "Update the call site to pass employeeId.", employeeName, to);
        sendCheckoutReminder(to, employeeName, date, time, headline, note, null);
    }

    // ════════════════════════════════════════════════════════════════════════
    // LEAVE EMAILS  — always sent (these ARE the leave notifications)
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendLeaveStatusEmail(String to, String employeeName,
                                     String status, String dates, String comment) {
        boolean approved   = "APPROVED".equalsIgnoreCase(status);
        String accentColor = approved ? "#15803D" : "#C0392B";
        String subject     = (approved ? "✅ Leave Approved" : "❌ Leave Rejected") + " — " + dates;
        String body = wrap(accentColor, approved ? "✅" : "❌",
                approved ? "Leave Request Approved" : "Leave Request Rejected",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your leave request has been <strong>" + status.toLowerCase() + "</strong>.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Period</td><td>" + dates + "</td></tr>" +
                        "<tr><td>Decision</td><td style='color:" + accentColor + ";font-weight:800;'>" + status + "</td></tr>" +
                        (comment != null && !comment.isBlank() ? "<tr><td>Comment</td><td>" + comment + "</td></tr>" : "") +
                        "</table>" +
                        (approved ? "<p style='color:#15803D;'>Please ensure work handover before leaving.</p>"
                                : "<p style='color:#C0392B;'>Please contact your manager for details.</p>")
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendLeaveAutoConsumed(String to, String employeeName,
                                      String date, String consumed, String remaining, boolean isHalfDay) {
        String subject = "📋 Leave Auto-Consumed — " + date;
        String body = wrap("#15803D", "📋", "Leave Balance Updated",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your leave balance has been automatically updated for <strong>" + date + "</strong>.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Date</td><td>" + date + "</td></tr>" +
                        "<tr><td>Type</td><td>" + (isHalfDay ? "Half Day" : "Full Day Absent") + "</td></tr>" +
                        "<tr><td>Leave Consumed</td><td style='color:#C0392B;font-weight:700;'>" + consumed + "</td></tr>" +
                        "<tr><td>Remaining Balance</td><td style='color:#15803D;font-weight:700;'>" + remaining + "</td></tr>" +
                        "</table>" +
                        "<p style='font-size:12px;color:#6B7A99;'>No salary deduction — sufficient leave balance was available.</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendSalaryDeductionWarning(String to, String employeeName,
                                           String date, String deduction, boolean isHalfDay) {
        String subject = "⚠️ Salary Deduction Applied — " + date;
        String body = wrap("#C0392B", "⚠️", "Salary Deduction Applied",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>Salary deduction for <strong>" + date + "</strong> — 0 leave balance remaining.</p></div>" +
                        "<table class='info'>" +
                        "<tr><td>Date</td><td>" + date + "</td></tr>" +
                        "<tr><td>Reason</td><td>" + (isHalfDay ? "Half Day" : "Absent") + " with no leaves left</td></tr>" +
                        "<tr><td>Deduction</td><td style='color:#C0392B;font-weight:700;'>" + deduction + " day salary deducted</td></tr>" +
                        "<tr><td>Leave Balance</td><td style='color:#C0392B;font-weight:700;'>0 days</td></tr>" +
                        "</table>" +
                        "<p style='font-size:12px;color:#6B7A99;'>This deduction will reflect in your salary slip.</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendLeaveRequestNotification(String adminEmail, String adminName,
                                             String employeeFullName, String leaveTypeName,
                                             String startDate, String endDate, String totalDays,
                                             boolean halfDay, String halfDaySlot, String reason) {
        String period = halfDay
                ? startDate + " (" + (halfDaySlot != null ? halfDaySlot : "Half Day") + ")"
                : startDate.equals(endDate) ? startDate : startDate + " → " + endDate;
        String subject = "📋 Leave Request — " + employeeFullName + " (" + leaveTypeName + ")";
        String body = wrap("#F59E0B", "📋", "New Leave Request",
                "<p>Hi <strong>" + adminName + "</strong>,</p>" +
                        "<p><strong>" + employeeFullName + "</strong> has applied for leave and requires your approval.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Employee</td><td><strong>" + employeeFullName + "</strong></td></tr>" +
                        "<tr><td>Leave Type</td><td>" + leaveTypeName + "</td></tr>" +
                        "<tr><td>Period</td><td><strong>" + period + "</strong></td></tr>" +
                        "<tr><td>Duration</td><td><strong>" + totalDays + "</strong></td></tr>" +
                        "<tr><td>Status</td><td style='color:#F59E0B;font-weight:800;'>PENDING — Awaiting Approval</td></tr>" +
                        "</table>" +
                        "<div class='highlight'><p>📝 Reason: " + reason + "</p></div>" +
                        "<p>Please log in to <strong>Finabits HRMS</strong> to review and approve or reject this request.</p>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app/leave' style='display:inline-block;padding:12px 28px;background:#1C3461;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:13px;'>Review Leave Request →</a>" +
                        "</p>" +
                        "<p style='font-size:11px;color:#9AA8BF;'>This is an automated notification. Do not reply to this email.</p>"
        );
        sendEmail(adminEmail, subject, body);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ANNOUNCEMENT & HOLIDAY EMAILS  — always sent to all users
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendAnnouncementEmail(String to, String employeeName,
                                      String title, String message) {
        String subject = "📢 New Announcement — " + title;
        String body = wrap("#1C3461", "📢", "New Announcement",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p><strong>" + title + "</strong></p></div>" +
                        "<p style='font-size:13.5px;color:#374151;line-height:1.8;'>" + message + "</p>" +
                        "<hr class='divider'/>" +
                        "<p style='font-size:12px;color:#9AA8BF;'>Log in to Sitegenius HRMS to view all announcements.</p>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;background:#1C3461;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:13px;'>View on HRMS →</a>" +
                        "</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendHolidayNotification(String to, String employeeName,
                                        String holidayName, String date, boolean optional) {
        String subject = "🏖️ Upcoming Holiday — " + holidayName + " on " + date;
        String body = wrap("#F59E0B", "🏖️", "Upcoming Holiday",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Reminder about an upcoming holiday.</p>" +
                        "<table class='info'>" +
                        "<tr><td>Holiday</td><td>" + holidayName + "</td></tr>" +
                        "<tr><td>Date</td><td><strong>" + date + "</strong></td></tr>" +
                        "<tr><td>Type</td><td>" + (optional ? "🔘 Optional" : "✅ Public Holiday") + "</td></tr>" +
                        "</table>" +
                        (optional ? "<p style='font-size:12px;color:#B45309;'>Optional holiday — check with your manager.</p>"
                                : "<p style='font-size:12px;color:#15803D;'>Public holiday — office will be closed.</p>") +
                        "<p>Enjoy your holiday! 🎉</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendHolidayCancelledEmail(String to, String employeeName,
                                          String holidayName, String date) {
        String subject = "❌ Holiday Cancelled — " + holidayName;
        String body = wrap("#C0392B", "❌", "Holiday Cancelled",
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<div class='highlight'><p>The following holiday has been <strong style='color:#C0392B;'>cancelled</strong>.</p></div>" +
                        "<table class='info'>" +
                        "<tr><td>Holiday</td><td>" + holidayName + "</td></tr>" +
                        "<tr><td>Date</td><td><strong>" + date + "</strong></td></tr>" +
                        "<tr><td>Status</td><td style='color:#C0392B;font-weight:800;'>CANCELLED</td></tr>" +
                        "</table>" +
                        "<p>This is a working day. Please plan your attendance accordingly.</p>"
        );
        sendEmail(to, subject, body);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ACCOUNT EMAILS  — always sent
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendPasswordResetEmail(String to, String name, String resetLink, int expiryHours) {
        String subject = "🔑 Reset Your Sitegenius HRMS Password";
        String body = wrap("#1C3461", "🔑", "Reset Your Password",
                "<p>Hi <strong>" + name + "</strong>,</p>" +
                        "<p>We received a request to reset your Sitegenius HRMS password.</p>" +
                        "<div class='highlight'><p>⏰ This link expires in <strong>" + expiryHours + " hour(s)</strong>.</p></div>" +
                        "<p style='text-align:center;margin:24px 0;'>" +
                        "<a href='" + resetLink + "' style='display:inline-block;padding:14px 32px;background:#1C3461;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:14px;'>Reset My Password →</a>" +
                        "</p>" +
                        "<p style='font-size:12px;color:#6B7A99;'>Or copy: <span style='font-family:monospace;font-size:11px;color:#2471A3;word-break:break-all;'>" + resetLink + "</span></p>" +
                        "<hr class='divider'/>" +
                        "<p style='font-size:11px;color:#9AA8BF;'>If you did not request this, ignore this email.</p>"
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendPasswordResetConfirmation(String to, String name) {
        String subject = "✅ Password Changed Successfully";
        String body = wrap("#15803D", "✅", "Password Changed",
                "<p>Hi <strong>" + name + "</strong>,</p>" +
                        "<div class='highlight'><p>Your password has been <strong>successfully changed</strong>.</p></div>" +
                        "<p style='text-align:center;margin:20px 0;'>" +
                        "<a href='https://hrms-fe-ten.vercel.app' style='display:inline-block;padding:12px 28px;background:#15803D;color:#fff;border-radius:10px;text-decoration:none;font-weight:800;font-size:13px;'>Go to Login →</a>" +
                        "</p>" +
                        "<hr class='divider'/>" +
                        "<p style='font-size:11px;color:#9AA8BF;'>If you did not make this change, contact your admin immediately.</p>"
        );
        sendEmail(to, subject, body);
    }
}