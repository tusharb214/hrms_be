package com.sitegenius.hrms.service;

import com.sitegenius.hrms.dto.request.LeaveActionRequest;
import com.sitegenius.hrms.dto.request.LeaveRequest;
import com.sitegenius.hrms.dto.response.LeaveBalanceResponse;
import com.sitegenius.hrms.dto.response.LeaveResponse;
import com.sitegenius.hrms.entity.Leave;
import com.sitegenius.hrms.entity.LeaveTypeConfig;
import com.sitegenius.hrms.entity.User;
import com.sitegenius.hrms.enums.LeaveStatus;
import com.sitegenius.hrms.enums.LeaveType;
import com.sitegenius.hrms.enums.Role;
import com.sitegenius.hrms.exception.BadRequestException;
import com.sitegenius.hrms.exception.ResourceNotFoundException;
import com.sitegenius.hrms.repository.LeaveRepository;
import com.sitegenius.hrms.repository.LeaveTypeConfigRepository;
import com.sitegenius.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveService {

    private final LeaveRepository           leaveRepository;
    private final UserRepository            userRepository;
    private final SystemSettingService      settingService;
    private final LeaveTypeConfigRepository leaveTypeConfigRepository;
    private final EmailService              emailService;
    private final NotificationService       notificationService;

    @Value("${app.upload.dir:/var/www/sitegeniusemployee/uploads}")
    private String uploadDir;

    // ── Apply leave with leave type + half day + document upload ─────────────
    public LeaveResponse applyLeave(LeaveRequest request, MultipartFile document) throws IOException {
        User user = getCurrentUser();

        if (request.getStartDate().isAfter(request.getEndDate()))
            throw new BadRequestException("Start date must be before or equal to end date");
        if (request.getStartDate().isBefore(LocalDate.now()))
            throw new BadRequestException("Cannot apply leave for past dates");

        // PATCH: Validate start date is not a Sunday (Sunday is a non-working day)
        validateNotSunday(request.getStartDate());

        // Resolve leave type config
        LeaveTypeConfig leaveTypeConfig = null;
        if (request.getLeaveTypeConfigId() != null) {
            leaveTypeConfig = leaveTypeConfigRepository.findById(request.getLeaveTypeConfigId())
                    .orElseThrow(() -> new BadRequestException("Invalid leave type"));

            if (!leaveTypeConfig.isActive())
                throw new BadRequestException("This leave type is not active");

            // ── Casual Leave: minimum 2 days advance notice ───────────────────
            if ("CASUAL".equalsIgnoreCase(leaveTypeConfig.getTypeCode())) {
                long daysFromToday = ChronoUnit.DAYS.between(LocalDate.now(), request.getStartDate());
                if (daysFromToday < 2) {
                    throw new BadRequestException(
                            "Casual Leave requires at least 2 days advance notice. " +
                                    "For tomorrow or today, please apply for Sick Leave instead."
                    );
                }
            }

            if (request.isHalfDay() && !leaveTypeConfig.isHalfDayAllowed())
                throw new BadRequestException(leaveTypeConfig.getTypeName() + " does not allow half day");

            if (leaveTypeConfig.isRequiresDocument() && (document == null || document.isEmpty()))
                throw new BadRequestException(leaveTypeConfig.getTypeName() + " requires a supporting document (e.g. medical certificate)");
        }

        // PATCH: Calculate days using Mon–Sat working days (Saturday IS a working day, Sunday is NOT)
        // OLD: int totalDays = request.isHalfDay() ? 1 : countWorkingDays(request.getStartDate(), request.getEndDate());
        // NEW: countWorkingDays now counts Mon–Sat, skipping only Sunday
        int totalDays = request.isHalfDay() ? 1 : countWorkingDays(request.getStartDate(), request.getEndDate());
        if (totalDays == 0) throw new BadRequestException("Selected dates have no working days");

        // Leave quota from type config OR global setting
        int allowedPerYear = leaveTypeConfig != null
                ? leaveTypeConfig.getAllowedPerYear()
                : settingService.getLeavesPerYear();

        int currentYear        = request.getStartDate().getYear();
        double alreadyUsedPaid = leaveRepository.sumApprovedLeavesByTypeAndYear(user, LeaveType.PAID, currentYear);
        double mixedPaid       = leaveRepository.sumApprovedMixedPaidByYear(user, currentYear);
        double totalPaidUsed   = alreadyUsedPaid + mixedPaid;
        double remainingPaid   = Math.max(0, allowedPerYear - totalPaidUsed);

        // PATCH: Use 0.5 for half-day consumed, otherwise use totalDays (Mon–Sat count)
        double daysToDeduct = request.isHalfDay() ? 0.5 : totalDays;

        double paidDays, unpaidDays;
        LeaveType leaveType;
        if (remainingPaid >= daysToDeduct) {
            paidDays = daysToDeduct; unpaidDays = 0; leaveType = LeaveType.PAID;
        } else if (remainingPaid == 0) {
            paidDays = 0; unpaidDays = daysToDeduct; leaveType = LeaveType.UNPAID;
        } else {
            paidDays = remainingPaid; unpaidDays = daysToDeduct - remainingPaid; leaveType = LeaveType.MIXED;
        }

        // Handle document upload
        String docPath = null, docName = null;
        if (document != null && !document.isEmpty()) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeName  = document.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
            String fileName  = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
            Path   dir       = Paths.get(uploadDir, String.valueOf(user.getId()), "LEAVE_DOCS");
            Files.createDirectories(dir);
            Path filePath    = dir.resolve(fileName);
            Files.copy(document.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            docPath = filePath.toString();
            docName = document.getOriginalFilename();
        }

        Leave leave = Leave.builder()
                .user(user)
                .leaveTypeConfig(leaveTypeConfig)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalDays(totalDays)
                .paidDays((int) paidDays)
                .unpaidDays((int) unpaidDays)
                .halfDay(request.isHalfDay())
                .halfDaySlot(request.getHalfDaySlot())
                .leaveType(leaveType)
                .leaveConsumed(daysToDeduct)
                .status(LeaveStatus.PENDING)
                .reason(request.getReason())
                .documentPath(docPath)
                .documentName(docName)
                .build();

        leaveRepository.save(leave);
        log.info("Leave applied: {} | type={} | total={} paid={} unpaid={} halfDay={}",
                user.getEmail(),
                leaveTypeConfig != null ? leaveTypeConfig.getTypeCode() : "GENERAL",
                totalDays, paidDays, unpaidDays, request.isHalfDay());

        // ── Email admins ──────────────────────────────────────────────────────
        try {
            String leaveTypeName  = leaveTypeConfig != null ? leaveTypeConfig.getTypeName() : "General Leave";
            String totalDaysLabel = request.isHalfDay() ? "Half Day (0.5)" : totalDays + " day(s)";
            String startStr       = request.getStartDate().toString();
            String endStr         = request.getEndDate().toString();

            List<User> admins = userRepository.findByRole(Role.ADMIN)
                    .stream().filter(User::isActive).toList();
            for (User admin : admins) {
                emailService.sendLeaveRequestNotification(
                        admin.getEmail(), admin.getFullName(),
                        user.getFullName(), leaveTypeName, startStr, endStr,
                        totalDaysLabel, request.isHalfDay(),
                        request.getHalfDaySlot(), request.getReason());
            }
            log.info("Leave request notification sent to {} admin(s) for employee {}",
                    admins.size(), user.getEmail());
        } catch (Exception emailEx) {
            log.error("Failed to send leave notification to admins: {}", emailEx.getMessage());
        }

        // ── In-app notification → all admins ──────────────────────────────────
        String leaveTypeName = leaveTypeConfig != null ? leaveTypeConfig.getTypeName() : "General Leave";
        notificationService.leaveApplied(
                user.getFullName(),
                leaveTypeName,
                request.getStartDate().toString(),
                request.getEndDate().toString(),
                leave.getId());

        return mapToResponse(leave);
    }

    // Backward compat — no document
    public LeaveResponse applyLeave(LeaveRequest request) {
        try { return applyLeave(request, null); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    // ── My leaves ─────────────────────────────────────────────────────────────
    public Page<LeaveResponse> getMyLeaves(Pageable pageable) {
        User user = getCurrentUser();
        return leaveRepository.findByUser(user, pageable).map(this::mapToResponse);
    }

    // ── My leave balance ──────────────────────────────────────────────────────
    public LeaveBalanceResponse getMyLeaveBalance() {
        return getLeaveBalanceByUserId(getCurrentUser().getId());
    }

    public LeaveBalanceResponse getLeaveBalanceByUserId(Long userId) {
        User user   = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        int year    = LocalDate.now().getYear();
        int allowed = settingService.getLeavesPerYear();

        double usedPaid    = leaveRepository.sumApprovedLeavesByTypeAndYear(user, LeaveType.PAID,   year);
        double usedUnpaid  = leaveRepository.sumApprovedLeavesByTypeAndYear(user, LeaveType.UNPAID, year);
        double mixedPaid   = leaveRepository.sumApprovedMixedPaidByYear(user, year);
        double mixedUnpaid = leaveRepository.sumApprovedMixedUnpaidByYear(user, year);

        double totalUsedPaid   = usedPaid   + mixedPaid;
        double totalUsedUnpaid = usedUnpaid + mixedUnpaid;

        return LeaveBalanceResponse.builder()
                .totalAllowed(allowed)
                .usedPaid(totalUsedPaid)
                .usedUnpaid(totalUsedUnpaid)
                .remainingPaid(Math.max(0, allowed - totalUsedPaid))
                .totalUsed(totalUsedPaid + totalUsedUnpaid)
                .year(year)
                .build();
    }

    public Page<LeaveResponse> getLeavesByUserId(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return leaveRepository.findByUser(user, pageable).map(this::mapToResponse);
    }

    public Page<LeaveResponse> getAllLeaves(Pageable pageable) {
        return leaveRepository.findAll(pageable).map(this::mapToResponse);
    }

    public Page<LeaveResponse> getLeavesByStatus(LeaveStatus status, Pageable pageable) {
        return leaveRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    public LeaveResponse actionLeave(Long leaveId, LeaveActionRequest request) {
        User  admin = getCurrentUser();
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + leaveId));
        if (leave.getStatus() != LeaveStatus.PENDING)
            throw new BadRequestException("Leave already actioned: " + leave.getStatus());
        if (request.getStatus() == LeaveStatus.PENDING)
            throw new BadRequestException("Action status cannot be PENDING");

        leave.setStatus(request.getStatus());
        leave.setAdminComment(request.getAdminComment());
        leave.setApprovedBy(admin);
        leave.setActionAt(LocalDateTime.now());
        leaveRepository.save(leave);
        log.info("Leave {} {} by {}", leaveId, request.getStatus(), admin.getEmail());

        // ── In-app notification → employee ────────────────────────────────────
        String leaveTypeName = leave.getLeaveTypeConfig() != null
                ? leave.getLeaveTypeConfig().getTypeName() : "General Leave";
        notificationService.leaveActioned(
                leave.getUser().getId(),
                request.getStatus().name(),
                leave.getLeaveType().name(),
                leave.getId());

        return mapToResponse(leave);
    }

    public LeaveResponse getLeaveById(Long id) {
        return mapToResponse(leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + id)));
    }

    // ── Download leave document ──────────────────────────────────────────────
    public ResponseEntity<Resource> downloadLeaveDocument(Long leaveId, String requesterEmail) throws Exception {
        Leave leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave not found: " + leaveId));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean isAdmin = requester.getRole().name().equals("ADMIN");
        if (!isAdmin && !leave.getUser().getId().equals(requester.getId()))
            throw new BadRequestException("Access denied");

        if (leave.getDocumentPath() == null)
            throw new ResourceNotFoundException("No document attached to this leave");

        java.nio.file.Path path = java.nio.file.Paths.get(leave.getDocumentPath());
        if (!java.nio.file.Files.exists(path))
            throw new ResourceNotFoundException("Document file not found on server");

        Resource resource = new PathResource(path);
        String contentType = java.nio.file.Files.probeContentType(path);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + leave.getDocumentName() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    // ── Admin: get all leaves with documents ─────────────────────────────────
    public List<LeaveResponse> getLeavesWithDocuments(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));
        return leaveRepository.findAll().stream()
                .filter(l -> l.getUser().getId().equals(userId))
                .filter(l -> l.getDocumentPath() != null)
                .map(this::mapToResponse)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * PATCH APPLIED: 6-day work week (Mon–Sat).
     *
     * Saturday IS a working day → counted as a leave day.
     * Sunday  is NOT a working day → skipped (not counted).
     *
     * OLD behaviour (Mon–Fri only):
     *   if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) count++;
     *
     * NEW behaviour (Mon–Sat):
     *   if (dow != DayOfWeek.SUNDAY) count++;
     */
    private int countWorkingDays(LocalDate start, LocalDate end) {
        int count = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            DayOfWeek dow = cur.getDayOfWeek();
            // PATCH: Only skip Sunday — Saturday now counts as a working day
            if (dow != DayOfWeek.SUNDAY) count++;
            cur = cur.plusDays(1);
        }
        return count;
    }

    /**
     * PATCH APPLIED: Validate that a leave start date is not a Sunday.
     * Employees must not be able to start a leave on a non-working day (Sunday).
     * Called inside applyLeave() before any other processing.
     */
    private void validateNotSunday(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new BadRequestException(
                    "Leave cannot start on a Sunday — Sunday is a non-working day.");
        }
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private LeaveResponse mapToResponse(Leave leave) {
        LeaveTypeConfig ltc = leave.getLeaveTypeConfig();
        return LeaveResponse.builder()
                .id(leave.getId())
                .userId(leave.getUser().getId())
                .employeeName(leave.getUser().getFullName())
                .leaveTypeConfigId(ltc != null ? ltc.getId()       : null)
                .leaveTypeName(ltc    != null ? ltc.getTypeName()  : "General Leave")
                .leaveTypeCode(ltc    != null ? ltc.getTypeCode()  : "GENERAL")
                .startDate(leave.getStartDate())
                .endDate(leave.getEndDate())
                .totalDays(leave.getTotalDays())
                .paidDays(leave.getPaidDays())
                .unpaidDays(leave.getUnpaidDays())
                .halfDay(leave.isHalfDay())
                .halfDaySlot(leave.getHalfDaySlot())
                .leaveType(leave.getLeaveType())
                .leaveConsumed(leave.getLeaveConsumed())
                .status(leave.getStatus())
                .reason(leave.getReason())
                .adminComment(leave.getAdminComment())
                .approvedByName(leave.getApprovedBy() != null ? leave.getApprovedBy().getFullName() : null)
                .appliedAt(leave.getAppliedAt())
                .actionAt(leave.getActionAt())
                .hasDocument(leave.getDocumentPath() != null)
                .documentName(leave.getDocumentName())
                .build();
    }
}