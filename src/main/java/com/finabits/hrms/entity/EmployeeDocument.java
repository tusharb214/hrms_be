package com.sitegenius.hrms.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmployeeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User user;

    @Column(nullable = false)
    private String documentType;    // "OFFER_LETTER", "ID_PROOF", "SALARY_SLIP", "MEDICAL_CERT", "OTHER"

    @Column(nullable = false)
    private String fileName;        // original file name

    @Column(nullable = false)
    private String filePath;        // server path

    @Column(nullable = false)
    private String fileType;        // MIME type e.g. application/pdf

    private Long fileSize;          // bytes

    private String description;     // optional note from admin

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User uploadedBy;        // admin who uploaded

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}