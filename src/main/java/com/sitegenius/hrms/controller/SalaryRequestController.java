package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.SalaryRequestResponse;
import com.sitegenius.hrms.dto.response.SalaryResponse;
import com.sitegenius.hrms.service.SalaryRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Salary Request")
public class SalaryRequestController {

    private final SalaryRequestService salaryRequestService;

    // ── Employee: submit a request ────────────────────────────────────────────
    @PostMapping("/employee/salary/request")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Employee requests a salary slip for a given month/year")
    public ResponseEntity<ApiResponse<SalaryRequestResponse>> submitRequest(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success(
                "Salary slip request submitted",
                salaryRequestService.submitRequest(month, year)));
    }

    // ── Employee: my requests ─────────────────────────────────────────────────
    @GetMapping("/employee/salary/requests")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my salary slip requests")
    public ResponseEntity<ApiResponse<Page<SalaryRequestResponse>>> getMyRequests(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "My salary requests",
                salaryRequestService.getMyRequests(PageRequest.of(page, size))));
    }

    // ── Admin: pending requests ───────────────────────────────────────────────
    @GetMapping("/admin/salary/requests/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all pending salary slip requests")
    public ResponseEntity<ApiResponse<Page<SalaryRequestResponse>>> getPending(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending salary requests",
                salaryRequestService.getPendingRequests(PageRequest.of(page, size))));
    }

    // ── Admin: all requests ───────────────────────────────────────────────────
    @GetMapping("/admin/salary/requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all salary slip requests")
    public ResponseEntity<ApiResponse<Page<SalaryRequestResponse>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "All salary requests",
                salaryRequestService.getAllRequests(PageRequest.of(page, size))));
    }

    // ── Admin: approve → auto-generate ───────────────────────────────────────
    @PostMapping("/admin/salary/requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a salary request and auto-generate the slip")
    public ResponseEntity<ApiResponse<SalaryResponse>> approve(
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Salary slip generated and approved",
                salaryRequestService.approveRequest(requestId)));
    }

    // ── Admin: reject ─────────────────────────────────────────────────────────
    @PostMapping("/admin/salary/requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a salary request")
    public ResponseEntity<ApiResponse<SalaryRequestResponse>> reject(
            @PathVariable Long requestId,
            @RequestParam(required = false, defaultValue = "") String reason) {
        return ResponseEntity.ok(ApiResponse.success(
                "Salary request rejected",
                salaryRequestService.rejectRequest(requestId, reason)));
    }
}