package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports & Export")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/attendance/excel")
    @Operation(summary = "Export attendance report as Excel")
    public ResponseEntity<byte[]> attendanceExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long userId) throws IOException {
        byte[] data = reportService.generateAttendanceReport(from, to, userId);
        return download(data, "attendance_report_" + from + "_to_" + to + ".xlsx");
    }

    @GetMapping("/leave/excel")
    @Operation(summary = "Export leave report as Excel")
    public ResponseEntity<byte[]> leaveExcel(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(required = false) Long userId) throws IOException {
        byte[] data = reportService.generateLeaveReport(year, userId);
        return download(data, "leave_report_" + year + ".xlsx");
    }

    @GetMapping("/leave-balance/excel")
    @Operation(summary = "Export leave balance report as Excel")
    public ResponseEntity<byte[]> leaveBalanceExcel(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) throws IOException {
        byte[] data = reportService.generateLeaveBalanceReport(year);
        return download(data, "leave_balance_" + year + ".xlsx");
    }

    @GetMapping("/salary/excel")
    @Operation(summary = "Export salary report as Excel")
    public ResponseEntity<byte[]> salaryExcel(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam(required = false) Long userId) throws IOException {
        byte[] data = reportService.generateSalaryReport(month, year, userId);
        return download(data, "salary_report_" + month + "_" + year + ".xlsx");
    }

    @GetMapping("/employee-leave/excel")
    @Operation(summary = "Export leave report for a specific employee — all leaves + balance per type")
    public ResponseEntity<byte[]> employeeLeaveExcel(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) throws IOException {
        byte[] data = reportService.generateEmployeeLeaveReport(userId, year);
        User user = null; // name in filename handled by service
        return download(data, "leave_report_employee_" + userId + "_" + year + ".xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}