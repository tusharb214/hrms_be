package com.finabits.hrms.controller;

import com.finabits.hrms.dto.request.SettingRequest;
import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.entity.SystemSetting;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.repository.UserRepository;
import com.finabits.hrms.service.EmailService;
import com.finabits.hrms.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "System Settings")
public class SettingsController {

    private final SystemSettingService settingService;
    private final EmailService         emailService;
    private final UserRepository       userRepository;

    @GetMapping
    @Operation(summary = "Get all system settings")
    public ResponseEntity<ApiResponse<List<SystemSetting>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Settings fetched", settingService.getAllSettings()));
    }

    @PostMapping
    @Operation(summary = "Create or update a setting (upsert)")
    public ResponseEntity<ApiResponse<SystemSetting>> upsert(@Valid @RequestBody SettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Setting saved", settingService.upsert(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a setting by ID")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        settingService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Setting deleted"));
    }

    // ── One-click: send check-in reminders NOW ───────────────────────────────
    @PostMapping("/send-checkin-reminders")
    @Operation(summary = "Manually trigger check-in reminder emails to all employees")
    public ResponseEntity<ApiResponse<String>> sendCheckinReminders(
            @RequestBody(required = false) Map<String, String> body) {

        String slot = (body != null && body.get("slot") != null)
                ? body.get("slot")
                : settingService.getCheckinTimes().stream().findFirst().orElse("now");

        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        employees.forEach(emp ->
                emailService.sendCheckinReminder(emp.getEmail(), emp.getFullName(), slot));

        String msg = "Check-in reminders sent to " + employees.size() + " employee(s) for slot: " + slot;
        return ResponseEntity.ok(ApiResponse.success(msg));
    }

    // ── One-click: send work summary reminders NOW ───────────────────────────
    @PostMapping("/send-summary-reminders")
    @Operation(summary = "Manually trigger work summary reminder emails to all employees")
    public ResponseEntity<ApiResponse<String>> sendSummaryReminders() {
        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        String today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));

        employees.forEach(emp ->
                emailService.sendWorkSummaryReminder(emp.getEmail(), emp.getFullName(), today));

        String msg = "Work summary reminders sent to " + employees.size() + " employee(s)";
        return ResponseEntity.ok(ApiResponse.success(msg));
    }

    // ── One-click: send custom broadcast to all employees ────────────────────
    @PostMapping("/send-broadcast")
    @Operation(summary = "Send a custom broadcast email to all active employees")
    public ResponseEntity<ApiResponse<String>> sendBroadcast(
            @RequestBody Map<String, String> body) {

        String subject = body.getOrDefault("subject", "Message from Sitegenius.Hrms");
        String message = body.get("message");
        if (message == null || message.isBlank())
            return ResponseEntity.badRequest().body(ApiResponse.error("Message is required"));

        List<User> employees = userRepository.findByRole(Role.EMPLOYEE)
                .stream().filter(User::isActive).toList();

        String htmlBody = "<p>" + message.replace("\n", "<br>") + "</p>";
        employees.forEach(emp ->
                emailService.sendEmail(emp.getEmail(), subject, htmlBody));

        String msg = "Broadcast sent to " + employees.size() + " employee(s)";
        return ResponseEntity.ok(ApiResponse.success(msg));
    }
}