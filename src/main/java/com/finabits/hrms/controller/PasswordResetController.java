package com.finabits.hrms.controller;

import com.finabits.hrms.dto.request.ForgotPasswordRequest;
import com.finabits.hrms.dto.request.ResetPasswordRequest;
import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Password Reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ── POST /api/auth/forgot-password ────────────────────────────────────────
    // Public — no auth required
    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        // Always return success — don't reveal if email exists
        return ResponseEntity.ok(ApiResponse.success(
                "If this email is registered, a reset link has been sent."));
    }

    // ── GET /api/auth/validate-reset-token?token=xxx ─────────────────────────
    // Public — validates token before showing reset form
    @GetMapping("/validate-reset-token")
    @Operation(summary = "Validate a password reset token")
    public ResponseEntity<ApiResponse<Map<String, String>>> validateToken(
            @RequestParam String token) {
        String email = passwordResetService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.success("Token is valid",
                Map.of("email", email)));
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────────
    // Public — resets password using token
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token from email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.getToken(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully! You can now log in."));
    }
}