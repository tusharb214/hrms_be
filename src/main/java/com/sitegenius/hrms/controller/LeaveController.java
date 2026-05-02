package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.request.LeaveActionRequest;
import com.sitegenius.hrms.dto.request.LeaveRequest;
import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.LeaveBalanceResponse;
import com.sitegenius.hrms.dto.response.LeaveResponse;
import com.sitegenius.hrms.enums.LeaveStatus;
import com.sitegenius.hrms.service.LeaveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Leave Management")
public class LeaveController {

    private final LeaveService leaveService;

    // ── Employee endpoints ────────────────────────────────────────────────────

    // Apply leave with optional document upload (multipart)
    @PostMapping(value = "/employee/leave/apply", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Apply for leave — supports document upload (e.g. medical certificate)")
    public ResponseEntity<ApiResponse<LeaveResponse>> applyLeave(
            @RequestPart("data") @Valid LeaveRequest request,
            @RequestPart(value = "document", required = false) MultipartFile document) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Leave applied successfully",
                leaveService.applyLeave(request, document)));
    }

    // JSON-only fallback (no document)
    @PostMapping(value = "/employee/leave/apply-simple", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Apply for leave (JSON only — no document upload)")
    public ResponseEntity<ApiResponse<LeaveResponse>> applyLeaveSimple(
            @Valid @RequestBody LeaveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Leave applied successfully",
                leaveService.applyLeave(request)));
    }

    @GetMapping("/employee/leave/my")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my leave history — filtered by type if provided")
    public ResponseEntity<ApiResponse<Page<LeaveResponse>>> getMyLeaves(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appliedAt").descending());
        return ResponseEntity.ok(ApiResponse.success("Leave history",
                leaveService.getMyLeaves(pageable)));
    }

    @GetMapping("/employee/leave/balance")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my leave balance for current year")
    public ResponseEntity<ApiResponse<LeaveBalanceResponse>> getMyLeaveBalance() {
        return ResponseEntity.ok(ApiResponse.success("Leave balance",
                leaveService.getMyLeaveBalance()));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @GetMapping("/admin/leaves")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get all leaves — filter by status, userId, or leaveTypeCode")
    public ResponseEntity<ApiResponse<Page<LeaveResponse>>> getAllLeaves(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LeaveStatus status,
            @RequestParam(required = false) Long userId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("appliedAt").descending());
        Page<LeaveResponse> result;
        if (userId != null)      result = leaveService.getLeavesByUserId(userId, pageable);
        else if (status != null) result = leaveService.getLeavesByStatus(status, pageable);
        else                     result = leaveService.getAllLeaves(pageable);
        return ResponseEntity.ok(ApiResponse.success("Leaves fetched", result));
    }

    @GetMapping("/admin/leaves/balance/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get leave balance for a specific employee")
    public ResponseEntity<ApiResponse<LeaveBalanceResponse>> getLeaveBalanceByUser(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Leave balance",
                leaveService.getLeaveBalanceByUserId(userId)));
    }

    @PutMapping("/admin/leaves/{id}/action")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve or reject a leave request")
    public ResponseEntity<ApiResponse<LeaveResponse>> actionLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Leave " + request.getStatus().name().toLowerCase(),
                leaveService.actionLeave(id, request)));
    }

    @GetMapping("/admin/leaves/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get leave by ID")
    public ResponseEntity<ApiResponse<LeaveResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Leave fetched",
                leaveService.getLeaveById(id)));
    }

    // ── Download leave supporting document ────────────────────────────────────
    @GetMapping("/leaves/{id}/document")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Download supporting document for a leave (employee can only download own)")
    public ResponseEntity<Resource> downloadLeaveDoc(
            @PathVariable Long id,
            org.springframework.security.core.Authentication auth) throws Exception {
        return leaveService.downloadLeaveDocument(id, auth.getName());
    }
}