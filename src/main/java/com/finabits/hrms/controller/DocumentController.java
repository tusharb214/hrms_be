package com.finabits.hrms.controller;

import com.finabits.hrms.dto.response.ApiResponse;
import com.finabits.hrms.entity.EmployeeDocument;
import com.finabits.hrms.service.DocumentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Document Management")
public class DocumentController {

    private final DocumentService documentService;

    // ── Admin: upload document for employee ───────────────────────────────────
    @PostMapping(value = "/admin/documents/upload/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeDocument>> upload(
            @PathVariable Long userId,
            @RequestParam String documentType,
            @RequestParam(required = false) String description,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(ApiResponse.success("Document uploaded",
                documentService.uploadDocument(userId, documentType, description, file)));
    }

    // ── Admin: get all documents for employee ─────────────────────────────────
    @GetMapping("/admin/documents/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<EmployeeDocument>>> getEmployeeDocs(
            @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Documents",
                documentService.getEmployeeDocuments(userId)));
    }

    // ── Admin: delete document ────────────────────────────────────────────────
    @DeleteMapping("/admin/documents/{docId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long docId) throws IOException {
        documentService.deleteDocument(docId);
        return ResponseEntity.ok(ApiResponse.success("Deleted", null));
    }

    // ── Employee: upload own document ────────────────────────────────────────
    @PostMapping(value = "/employee/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<EmployeeDocument>> uploadOwn(
            @RequestParam String documentType,
            @RequestParam(required = false) String description,
            @RequestPart("file") MultipartFile file) throws IOException {
        // Employee uploads for themselves — get their own ID from security context
        String email = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        com.finabits.hrms.repository.UserRepository userRepo =
                documentService.getUserRepo();
        Long userId = userRepo.findByEmail(email).orElseThrow().getId();
        return ResponseEntity.ok(ApiResponse.success("Document uploaded",
                documentService.uploadDocument(userId, documentType, description, file)));
    }

    // ── Employee: get own documents ───────────────────────────────────────────
    @GetMapping("/employee/documents")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<List<EmployeeDocument>>> getMyDocs() {
        return ResponseEntity.ok(ApiResponse.success("My documents",
                documentService.getMyDocuments()));
    }

    // ── Download (both admin and employee — employee restricted to own) ───────
    @GetMapping("/documents/download/{docId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Resource> download(
            @PathVariable Long docId,
            @RequestHeader(value = "X-Role", required = false) String role) throws IOException {
        boolean isAdmin = role != null && role.contains("ADMIN");
        EmployeeDocument doc  = documentService.getDocById(docId);
        Path              path = documentService.getFilePath(docId, isAdmin);
        Resource          res  = new PathResource(path);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        doc.getFileType() != null ? doc.getFileType() : "application/octet-stream"))
                .body(res);
    }
}