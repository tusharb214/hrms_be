package com.finabits.hrms.controller;

import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.dto.response.SalaryResponse;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.repository.UserRepository;
import com.finabits.hrms.service.SalaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Salary Management")
public class SalaryController {

    private final SalaryService  salaryService;
    private final UserRepository userRepository;

    // ── Admin: generate salary slip ───────────────────────────────────────────
    @PostMapping("/admin/salary/generate/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Generate salary slip for employee")
    public ResponseEntity<ApiResponse<SalaryResponse>> generate(
            @PathVariable Long userId,
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success("Salary generated",
                salaryService.generateSalary(userId, month, year)));
    }

    // ── Admin: get salary history for any employee ────────────────────────────
    @GetMapping("/admin/salary/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get salary history for an employee")
    public ResponseEntity<ApiResponse<Page<SalaryResponse>>> getEmployeeSalaryHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success("Salary history",
                salaryService.getEmployeeSalaryHistory(userId, PageRequest.of(page, size))));
    }

    // ── Admin: get specific slip ──────────────────────────────────────────────
    @GetMapping("/admin/salary/{userId}/slip")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get specific salary slip")
    public ResponseEntity<ApiResponse<SalaryResponse>> getSlip(
            @PathVariable Long userId,
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.success("Salary slip fetched",
                salaryService.getSalarySlip(userId, month, year)));
    }

    // ── Employee: get own salary history ─────────────────────────────────────
    @GetMapping("/employee/salary/history")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Get my salary history")
    public ResponseEntity<ApiResponse<Page<SalaryResponse>>> getMySalaryHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.success("My salary history",
                salaryService.getMySalaryHistory(PageRequest.of(page, size))));
    }

    // ── Employee: download OWN salary slip as PDF ─────────────────────────────
    @GetMapping("/employee/salary/pdf/{month}/{year}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    @Operation(summary = "Download my salary slip as PDF")
    public ResponseEntity<byte[]> downloadMyPdf(
            @PathVariable int month,
            @PathVariable int year) throws Exception {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        byte[] pdf = salaryService.generatePdf(currentUser.getId(), month, year);

        // Filename: EmployeeName_SalarySlip_March2025.pdf
        String filename = salaryService.buildPdfFileName(currentUser.getId(), month, year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    // ── Admin: download PDF for any employee ─────────────────────────────────
    @GetMapping("/admin/salary/{userId}/pdf/{month}/{year}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Download salary slip PDF for any employee")
    public ResponseEntity<byte[]> downloadPdfForEmployee(
            @PathVariable Long userId,
            @PathVariable int month,
            @PathVariable int year) throws Exception {

        byte[] pdf = salaryService.generatePdf(userId, month, year);

        // Filename: EmployeeName_SalarySlip_March2025.pdf
        String filename = salaryService.buildPdfFileName(userId, month, year);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }
}