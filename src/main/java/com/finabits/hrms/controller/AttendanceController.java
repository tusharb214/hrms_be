package com.finabits.hrms.controller;

import com.finabits.hrms.dto.request.AttendanceRequest;
import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.dto.response.AttendanceDayResponse;
import com.finabits.hrms.dto.response.AttendanceResponse;
import com.finabits.hrms.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance Management")
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ── Employee ──────────────────────────────────────────────────────────────

    @PostMapping("/employee/attendance/checkin")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Check in for a configured slot")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Check-in recorded",
                attendanceService.checkIn(request)));
    }

    @PostMapping("/employee/attendance/checkout")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Check out for today — captures time and calculates total hours")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut() {
        return ResponseEntity.ok(ApiResponse.success("Checked out successfully",
                attendanceService.checkOut()));
    }

    @GetMapping("/employee/attendance/checkout-status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get today's check-in/checkout status and working hours")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCheckoutStatus() {
        return ResponseEntity.ok(ApiResponse.success("Checkout status",
                attendanceService.getCheckoutStatus()));
    }

    @GetMapping("/employee/attendance/today")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my today's attendance")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMyTodayAttendance() {
        return ResponseEntity.ok(ApiResponse.success("Today's attendance",
                attendanceService.getMyTodayAttendance()));
    }

    @GetMapping("/employee/attendance/history")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my attendance history (paginated)")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getMyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("Attendance history",
                attendanceService.getMyHistory(pageable)));
    }

    @GetMapping("/employee/attendance/slots")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get configured check-in slots")
    public ResponseEntity<ApiResponse<List<String>>> getSlots() {
        return ResponseEntity.ok(ApiResponse.success("Configured slots",
                attendanceService.getConfiguredSlots()));
    }

    @GetMapping("/employee/attendance/full-history")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Full slot breakdown per day — shows checked/missed/checkout")
    public ResponseEntity<ApiResponse<List<AttendanceDayResponse>>> getMyFullHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Full history",
                attendanceService.getMyFullHistory(from, to)));
    }

    @GetMapping("/employee/attendance/today-all")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Today's attendance for all employees")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getTodayAll() {
        return ResponseEntity.ok(ApiResponse.success("Today all",
                attendanceService.getTodayAllAttendance()));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/attendance/all")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "All attendance paginated")
    public ResponseEntity<ApiResponse<Page<AttendanceResponse>>> getAllAttendance(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "2000") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        return ResponseEntity.ok(ApiResponse.success("All attendance",
                attendanceService.getAllAttendance(pageable)));
    }

    @GetMapping("/admin/attendance/not-checked-out")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Employees who checked in today but have NOT checked out yet")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getNotCheckedOut() {
        return ResponseEntity.ok(ApiResponse.success("Not checked out",
                attendanceService.getNotCheckedOutToday()));
    }

    @GetMapping("/admin/attendance/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get attendance for a specific employee in date range")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getEmployeeAttendance(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Attendance fetched",
                attendanceService.getAttendanceByUserAndRange(userId, from, to)));
    }
}