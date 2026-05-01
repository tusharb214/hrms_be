package com.finabits.hrms.service;

import com.finabits.hrms.entity.EmployeeDocument;
import com.finabits.hrms.entity.User;
import com.finabits.hrms.exception.BadRequestException;
import com.finabits.hrms.exception.ResourceNotFoundException;
import com.finabits.hrms.repository.EmployeeDocumentRepository;
import com.finabits.hrms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final EmployeeDocumentRepository docRepo;
    private final UserRepository             userRepo;

    @Value("${app.upload.dir:/var/www/finabitsemployeeBE/uploads}")
    private String uploadDir;

    // ── Admin: upload document for employee ──────────────────────────────────
    public EmployeeDocument uploadDocument(Long userId, String documentType,
                                           String description, MultipartFile file) throws IOException {
        User admin = getCurrentUser();
        User emp   = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + userId));

        validateFile(file);

        // Build path: uploads/userId/documentType/timestamp_uuid_filename
        String timestamp  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeName   = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName   = timestamp + "_" + UUID.randomUUID().toString().substring(0,8) + "_" + safeName;
        Path   dir        = Paths.get(uploadDir, String.valueOf(userId), documentType);
        Files.createDirectories(dir);
        Path   filePath   = dir.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        EmployeeDocument doc = EmployeeDocument.builder()
                .user(emp)
                .documentType(documentType.toUpperCase())
                .fileName(file.getOriginalFilename())
                .filePath(filePath.toString())
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .description(description)
                .uploadedBy(admin)
                .build();

        docRepo.save(doc);
        log.info("Document uploaded: {} for employee {} by {}", documentType, emp.getEmail(), admin.getEmail());
        return doc;
    }

    // ── Get all documents for an employee ────────────────────────────────────
    public List<EmployeeDocument> getEmployeeDocuments(Long userId) {
        return docRepo.findByUserId(userId);
    }

    // ── Employee: get own documents only ─────────────────────────────────────
    public List<EmployeeDocument> getMyDocuments() {
        User user = getCurrentUser();
        return docRepo.findByUser(user);
    }

    // ── Download file ─────────────────────────────────────────────────────────
    public Path getFilePath(Long docId, boolean isAdmin) throws IOException {
        EmployeeDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));

        // Security: employee can only access their own docs
        if (!isAdmin) {
            User user = getCurrentUser();
            if (!doc.getUser().getId().equals(user.getId()))
                throw new BadRequestException("Access denied");
        }

        Path path = Paths.get(doc.getFilePath());
        if (!Files.exists(path))
            throw new ResourceNotFoundException("File not found on server");
        return path;
    }

    public EmployeeDocument getDocById(Long docId) {
        return docRepo.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
    }

    // ── Delete document ───────────────────────────────────────────────────────
    public void deleteDocument(Long docId) throws IOException {
        EmployeeDocument doc = docRepo.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        // Delete file from disk
        Path path = Paths.get(doc.getFilePath());
        Files.deleteIfExists(path);
        docRepo.delete(doc);
        log.info("Document deleted: {}", doc.getFileName());
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new BadRequestException("File is empty");
        if (file.getSize() > 10 * 1024 * 1024) throw new BadRequestException("File too large. Max 10MB.");
        String ct = file.getContentType();
        if (ct == null || (!ct.contains("pdf") && !ct.contains("image") &&
                !ct.contains("word") && !ct.contains("excel") && !ct.contains("sheet")))
            throw new BadRequestException("Invalid file type. Allowed: PDF, images, Word, Excel.");
    }

    // Expose userRepo for controller use
    public UserRepository getUserRepo() { return userRepo; }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}