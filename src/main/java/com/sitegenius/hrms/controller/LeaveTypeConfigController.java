package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.entity.LeaveTypeConfig;
import com.sitegenius.hrms.service.LeaveTypeConfigService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Leave Types")
public class LeaveTypeConfigController {

    private final LeaveTypeConfigService service;

    // Both roles can read active types (for apply form)
    @GetMapping("/leave-types")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<LeaveTypeConfig>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success("Leave types", service.getActive()));
    }

    @GetMapping("/admin/leave-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<LeaveTypeConfig>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("All leave types", service.getAll()));
    }

    @PostMapping("/admin/leave-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeConfig>> create(@RequestBody LeaveTypeConfig req) {
        return ResponseEntity.ok(ApiResponse.success("Leave type created", service.create(req)));
    }

    @PutMapping("/admin/leave-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTypeConfig>> update(
            @PathVariable Long id, @RequestBody LeaveTypeConfig req) {
        return ResponseEntity.ok(ApiResponse.success("Updated", service.update(id, req)));
    }

    @DeleteMapping("/admin/leave-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }
}