package com.finabits.hrms.controller;

import com.finabits.hrms.dto.request.AnnouncementRequest;
import com.finabits.hrms.dto.response.AnnouncementResponse;
import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping("/admin/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create announcement")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Announcement created", announcementService.create(request)));
    }

    @GetMapping("/announcements")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get all active announcements (paginated)")
    public ResponseEntity<ApiResponse<Page<AnnouncementResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                ApiResponse.success("Announcements", announcementService.getAll(pageable)));
    }

    @DeleteMapping("/admin/announcements/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate announcement")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Announcement removed"));
    }
}