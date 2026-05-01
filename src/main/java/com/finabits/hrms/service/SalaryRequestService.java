package com.finabits.hrms.service;

import com.finabits.hrms.dto.response.SalaryRequestResponse;
import com.finabits.hrms.dto.response.SalaryResponse;
import com.finabits.hrms.enums.Salary;
import com.finabits.hrms.entity.SalaryRequest;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.enums.Role;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.repository.SalaryRequestRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryRequestService {

    private final SalaryRequestRepository requestRepository;
    private final UserRepository          userRepository;
    private final SalaryService           salaryService;
    private final NotificationService     notificationService;   // ← ADDED

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE: submit a new request
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SalaryRequestResponse submitRequest(int month, int year) {
        User currentUser = currentUser();

        if (month < 1 || month > 12) throw new BadRequestException("Invalid month");
        if (year < 2000 || year > 2100) throw new BadRequestException("Invalid year");

        // Block current month and future months
        java.time.YearMonth requested = java.time.YearMonth.of(year, month);
        java.time.YearMonth current   = java.time.YearMonth.now();
        if (!requested.isBefore(current)) {
            throw new BadRequestException(
                    "You can only request a salary slip for a previous month. " +
                            "Current or future months are not allowed.");
        }

        // Prevent duplicate request for the same period
        if (requestRepository.existsByUserAndMonthAndYear(currentUser, month, year)) {
            throw new BadRequestException(
                    "You already have a salary-slip request for this period. Please wait for admin action.");
        }

        // Reject if salary already generated for that period
        if (salaryService.existsForUser(currentUser.getId(), month, year)) {
            throw new BadRequestException(
                    "Your salary slip for this period has already been generated.");
        }

        SalaryRequest req = SalaryRequest.builder()
                .user(currentUser)
                .month(month)
                .year(year)
                .status(Salary.PENDING)
                .build();

        SalaryRequest saved = requestRepository.save(req);

        // ── Notify all active admins ──────────────────────────────────────────
        try {
            userRepository.findByRole(Role.ADMIN).stream()
                    .filter(User::isActive)
                    .forEach(admin -> notificationService.create(
                            admin.getId(),
                            "SALARY_REQUEST",
                            "💰 Salary Slip Request",
                            currentUser.getFullName() + " requested a slip for "
                                    + getMonthName(month) + " " + year,
                            saved.getId(),
                            "SALARY"
                    ));
        } catch (Exception e) {
            log.error("Salary request notification failed for user {}: {}", currentUser.getId(), e.getMessage());
        }

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EMPLOYEE: my own request list
    // ─────────────────────────────────────────────────────────────────────────

    public Page<SalaryRequestResponse> getMyRequests(Pageable pageable) {
        return requestRepository
                .findByUserOrderByRequestedAtDesc(currentUser(), pageable)
                .map(this::toResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ADMIN: all pending requests
    // ─────────────────────────────────────────────────────────────────────────

    public Page<SalaryRequestResponse> getPendingRequests(Pageable pageable) {
        return requestRepository
                .findByStatusOrderByRequestedAtAsc(Salary.PENDING, pageable)
                .map(this::toResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ADMIN: all requests (any status)
    // ─────────────────────────────────────────────────────────────────────────

    public Page<SalaryRequestResponse> getAllRequests(Pageable pageable) {
        return requestRepository
                .findAllByOrderByRequestedAtDesc(pageable)
                .map(this::toResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ADMIN: approve → auto-generate salary
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SalaryResponse approveRequest(Long requestId) {
        SalaryRequest req = findById(requestId);

        if (req.getStatus() != Salary.PENDING) {
            throw new BadRequestException("Request is already " + req.getStatus());
        }

        // Generate the salary slip
        SalaryResponse salary = salaryService.generateSalary(
                req.getUser().getId(), req.getMonth(), req.getYear());

        req.setStatus(Salary.APPROVED);
        req.setSalaryId(salary.getId());
        requestRepository.save(req);

        // ── Notify the employee ───────────────────────────────────────────────
        try {
            notificationService.create(
                    req.getUser().getId(),
                    "SALARY_APPROVED",
                    "✅ Salary Slip Ready",
                    "Your salary slip for " + getMonthName(req.getMonth()) + " " + req.getYear()
                            + " has been generated. You can now download it.",
                    requestId,
                    "SALARY"
            );
        } catch (Exception e) {
            log.error("Salary approve notification failed for user {}: {}", req.getUser().getId(), e.getMessage());
        }

        return salary;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ADMIN: reject
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public SalaryRequestResponse rejectRequest(Long requestId, String reason) {
        SalaryRequest req = findById(requestId);

        if (req.getStatus() != Salary.PENDING) {
            throw new BadRequestException("Request is already " + req.getStatus());
        }

        req.setStatus(Salary.REJECTED);
        req.setRejectionReason(reason);
        SalaryRequest saved = requestRepository.save(req);

        // ── Notify the employee ───────────────────────────────────────────────
        try {
            String reasonText = (reason != null && !reason.isBlank())
                    ? " Reason: " + reason
                    : "";
            notificationService.create(
                    req.getUser().getId(),
                    "SALARY_REJECTED",
                    "❌ Salary Request Rejected",
                    "Your salary request for " + getMonthName(req.getMonth()) + " " + req.getYear()
                            + " was rejected." + reasonText,
                    requestId,
                    "SALARY"
            );
        } catch (Exception e) {
            log.error("Salary reject notification failed for user {}: {}", req.getUser().getId(), e.getMessage());
        }

        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private SalaryRequest findById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Salary request not found"));
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    private static final String[] MONTH_NAMES = {
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
    };

    private String getMonthName(int month) {
        if (month < 1 || month > 12) return String.valueOf(month);
        return MONTH_NAMES[month - 1];
    }

    private SalaryRequestResponse toResponse(SalaryRequest r) {
        User u = r.getUser();
        return SalaryRequestResponse.builder()
                .id(r.getId())
                .userId(u.getId())
                .employeeCode(u.getEmployeeCode())
                .employeeName(u.getFullName())
                .email(u.getEmail())
                .month(r.getMonth())
                .year(r.getYear())
                .salary(r.getStatus())
                .rejectionReason(r.getRejectionReason())
                .salaryId(r.getSalaryId())
                .requestedAt(r.getRequestedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}