package com.waqiti.business.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "business_tax_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BusinessTaxDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "document_type", nullable = false, length = 100)
    private String documentType;

    @Column(name = "tax_year", nullable = false)
    private Integer year;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.GENERATED;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submission_reference", length = 100)
    private String submissionReference;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    public enum DocumentStatus {
        GENERATED,
        SUBMITTED,
        ACCEPTED,
        REJECTED,
        AMENDED
    }
}