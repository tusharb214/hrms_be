package com.sitegenius.hrms.controller;

import com.sitegenius.hrms.dto.response.ApiResponse;
import com.sitegenius.hrms.entity.Notification;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.repository.UserRepository;
import com.sitegenius.hrms.service.NotificationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository      userRepository;

    // ── Resolve current user from JWT token (same pattern as all other controllers) ──
    private User resolveUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    /**
     * GET /api/notifications/count
     * Polled every 5s by the frontend bell.
     */
    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            Authentication authentication) {
        User user  = resolveUser(authentication);
        long count = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Unread count", Map.of("count", count)));
    }

    /**
     * GET /api/notifications
     * Returns 20 most recent notifications. Called when dropdown opens.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<Notification>>> getRecent(
            Authentication authentication) {
        User user = resolveUser(authentication);
        List<Notification> list = notificationService.getRecent(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Notifications", list));
    }

    /**
     * GET /api/notifications/paged?page=0&size=20
     */
    @GetMapping("/paged")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Page<Notification>>> getPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = resolveUser(authentication);
        Page<Notification> result = notificationService.getPaginated(user.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success("Notifications paged", result));
    }

    /**
     * PUT /api/notifications/read-all
     * Marks all as read. Called when bell dropdown opens.
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<String>> markAllRead(
            Authentication authentication) {
        User user = resolveUser(authentication);
        notificationService.markAllRead(user.getId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    /**
     * PUT /api/notifications/{id}/read
     * Marks a single notification as read.
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<String>> markOneRead(
            @PathVariable Long id,
            Authentication authentication) {
        User user = resolveUser(authentication);
        notificationService.markOneRead(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }
}