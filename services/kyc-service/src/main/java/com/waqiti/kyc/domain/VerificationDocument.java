package com.waqiti.kyc.domain;

import com.waqiti.kyc.service.KYCDocumentVerificationService.DocumentType;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "verification_documents", indexes = {
    @Index(name = "idx_ver_doc_user", columnList = "user_id"),
    @Index(name = "idx_ver_doc_type", columnList = "document_type"),
    @Index(name = "idx_ver_doc_status", columnList = "status"),
    @Index(name = "idx_ver_doc_key", columnList = "document_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"documentKey"})
@EqualsAndHashCode(exclude = {"documentKey"})
public class VerificationDocument {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "document_key", unique = true, nullable = false)
    private String documentKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "encryption_key_id")
    private String encryptionKeyId;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "scheduled_deletion_at")
    private LocalDateTime scheduledDeletionAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.UPLOADED;
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessedAt = LocalDateTime.now();
    }

    public enum Status {
        UPLOADED,           // Document uploaded
        PROCESSING,         // Being processed
        VERIFIED,          // Verified and valid
        REJECTED,          // Rejected
        ACTIVE,            // Active and available
        ARCHIVED,          // Archived
        PENDING_DELETION,  // Scheduled for deletion
        DELETED            // Deleted
    }
}