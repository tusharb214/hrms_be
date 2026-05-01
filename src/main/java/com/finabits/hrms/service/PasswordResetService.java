package com.finabits.hrms.service;

import com.finabits.hrms.entity.PasswordResetToken;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.repository.PasswordResetTokenRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository               userRepository;
    private final PasswordEncoder              passwordEncoder;
    private final EmailService                 emailService;

    @Value("${app.frontend.url:https://employees.finabits.in}")
    private String frontendUrl;

    private static final int TOKEN_EXPIRY_HOURS = 1;

    // ── Step 1: Request reset — send email ────────────────────────────────────
    public void requestReset(String email) {
        // Always return success even if email not found — prevents email enumeration
        userRepository.findByEmail(email).ifPresent(user -> {

            // Delete any existing tokens for this user
            tokenRepository.deleteAllByUser(user);

            // Generate secure random token
            String token = UUID.randomUUID().toString().replace("-", "") +
                    UUID.randomUUID().toString().replace("-", "");

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                    .used(false)
                    .build();

            tokenRepository.save(resetToken);

            String resetLink = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(
                    user.getEmail(), user.getFullName(), resetLink, TOKEN_EXPIRY_HOURS);

            log.info("Password reset token generated for: {}", email);
        });
    }

    // ── Step 2: Validate token (used by frontend to verify before showing form) ─
    public String validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link."));

        if (!resetToken.isValid()) {
            if (resetToken.isExpired()) throw new BadRequestException("Reset link has expired. Please request a new one.");
            if (resetToken.isUsed())   throw new BadRequestException("This reset link has already been used.");
        }

        return resetToken.getUser().getEmail();
    }

    // ── Step 3: Reset password using token ───────────────────────────────────
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword))
            throw new BadRequestException("Passwords do not match.");

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link."));

        if (!resetToken.isValid()) {
            if (resetToken.isExpired()) throw new BadRequestException("Reset link has expired. Please request a new one.");
            throw new BadRequestException("This reset link has already been used.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Mark token as used — cannot be reused
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Password reset successfully for: {}", user.getEmail());

        // Send confirmation email
        emailService.sendPasswordResetConfirmation(user.getEmail(), user.getFullName());
    }

    // ── Cleanup expired tokens — runs every hour ──────────────────────────────
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredTokens() {
        tokenRepository.deleteAllExpired(LocalDateTime.now());
        log.debug("Expired password reset tokens cleaned up");
    }
}