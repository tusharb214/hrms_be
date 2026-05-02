package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.request.HolidayRequest;
import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.entity.Holiday;
import com.sitegenius.hrms.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Holidays")
public class HolidayController {

    private final HolidayService holidayService;

    // ── Add single holiday ────────────────────────────────────────────────────
    @PostMapping("/admin/holidays")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a single holiday")
    public ResponseEntity<ApiResponse<Holiday>> create(@Valid @RequestBody HolidayRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Holiday added", holidayService.create(request)));
    }

    // ── Add holiday range (e.g. Ganpati vacation 2–12 Sept) ──────────────────
    @PostMapping("/admin/holidays/range")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add holiday for a date range — creates one entry per working day")
    public ResponseEntity<ApiResponse<List<Holiday>>> createRange(@Valid @RequestBody HolidayRequest request) {
        List<Holiday> holidays = holidayService.createRange(request);
        return ResponseEntity.ok(ApiResponse.success(
                holidays.size() + " holiday day(s) added for '" + request.getName() + "'",
                holidays));
    }

    // ── Get upcoming ──────────────────────────────────────────────────────────
    @GetMapping("/holidays/upcoming")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get all upcoming holidays")
    public ResponseEntity<ApiResponse<List<Holiday>>> getUpcoming() {
        return ResponseEntity.ok(ApiResponse.success("Upcoming holidays", holidayService.getUpcoming()));
    }

    // ── Get by range ──────────────────────────────────────────────────────────
    @GetMapping("/holidays")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get holidays in a date range")
    public ResponseEntity<ApiResponse<List<Holiday>>> getByRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Holidays", holidayService.getByRange(from, to)));
    }

    // ── Delete single ─────────────────────────────────────────────────────────
    @DeleteMapping("/admin/holidays/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a single holiday by ID")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        holidayService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Holiday deleted"));
    }

    // ── Delete entire range by name ───────────────────────────────────────────
    @DeleteMapping("/admin/holidays/range")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete all holidays with a given name between two dates")
    public ResponseEntity<ApiResponse<Void>> deleteRange(
            @RequestParam String name,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        int count = holidayService.deleteByNameAndRange(name, from, to);
        return ResponseEntity.ok(ApiResponse.success(count + " holiday day(s) deleted"));
    }
}