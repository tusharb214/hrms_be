package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.request.WorkSummaryRequest;
import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.WorkSummaryResponse;
import com.sitegenius.hrms.service.WorkSummaryService;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Work Summary")
public class WorkSummaryController {

    private final WorkSummaryService summaryService;

    // ── Employee: ONLY their own data ─────────────────────────────────────────

    @PostMapping("/employee/work-summary")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Submit today's work summary")
    public ResponseEntity<ApiResponse<WorkSummaryResponse>> submit(
            @Valid @RequestBody WorkSummaryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Work summary submitted!",
                summaryService.submitSummary(request)));
    }

    @GetMapping("/employee/work-summary/today")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get today's work summary")
    public ResponseEntity<ApiResponse<WorkSummaryResponse>> getToday() {
        return ResponseEntity.ok(ApiResponse.success("Today's summary",
                summaryService.getTodaySummary()));
    }

    @GetMapping("/employee/work-summary/history")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get MY work summary history only — with optional date/month filter")
    public ResponseEntity<ApiResponse<Page<WorkSummaryResponse>>> getMyHistory(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("My summary history",
                summaryService.getMyHistory(pageable, month, year, date)));
    }

    // ── Admin: MUST provide userId — no bulk data exposure ────────────────────

    @GetMapping("/admin/work-summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get work summaries for a SPECIFIC employee — userId is REQUIRED")
    public ResponseEntity<ApiResponse<Page<WorkSummaryResponse>>> getByEmployee(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("Employee summaries",
                summaryService.getEmployeeSummaries(userId, pageable, month, year, date)));
    }

    @GetMapping("/admin/work-summary/missing-today")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Employees who haven't submitted today")
    public ResponseEntity<ApiResponse<List<String>>> getMissingToday() {
        return ResponseEntity.ok(ApiResponse.success("Missing submissions",
                summaryService.getMissingSubmissionsToday()));
    }

    @GetMapping("/admin/work-summary/{userId}/{date}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get summary for specific employee on specific date")
    public ResponseEntity<ApiResponse<WorkSummaryResponse>> getByEmployeeAndDate(
            @PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success("Summary fetched",
                summaryService.getSummaryByEmployeeAndDate(userId, date)));
    }
}