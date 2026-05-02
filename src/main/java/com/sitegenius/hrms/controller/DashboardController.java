package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.DashboardResponse;
import com.sitegenius.hrms.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin dashboard stats")
    public ResponseEntity<ApiResponse<DashboardResponse>> getAdminStats() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", dashboardService.getStats()));
    }

    @GetMapping("/api/employee/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Employee dashboard stats")
    public ResponseEntity<ApiResponse<DashboardResponse>> getEmployeeDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", dashboardService.getEmployeeDashboard()));
    }

    @GetMapping("/api/employee/team-today")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Today team status — present, WFH, on leave, not checked in")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTeamToday() {
        return ResponseEntity.ok(ApiResponse.success("Team today", dashboardService.getTeamToday()));
    }
    @GetMapping("/api/employee/not-checked-out")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Employees who haven't checked out today")
    public ResponseEntity<ApiResponse<Object>> getNotCheckedOutEmployee() {
        return ResponseEntity.ok(ApiResponse.success("Not checked out", dashboardService.getNotCheckedOut()));
    }
}