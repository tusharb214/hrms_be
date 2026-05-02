package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.request.UpdateEmployeeRequest;
import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.dto.response.UserResponse;
import com.sitegenius.hrms.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employee Management")
public class EmployeeController {

    private final EmployeeService employeeService;

    // ── Admin endpoints ────────────────────────────────────────────────────

    @GetMapping("/api/admin/employees")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all employees (paginated)")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        return ResponseEntity.ok(ApiResponse.success("Employees fetched", employeeService.getAllEmployees(pageable)));
    }

    @GetMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get employee by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Employee fetched", employeeService.getById(id)));
    }

    @PutMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update employee")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Employee updated", employeeService.update(id, request)));
    }

    @DeleteMapping("/api/admin/employees/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Soft-delete employee")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Employee deactivated"));
    }

    // ── Employee endpoints ─────────────────────────────────────────────────

    @GetMapping("/api/employee/profile")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get own profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched",
                employeeService.getProfile(userDetails.getUsername())));
    }
}
