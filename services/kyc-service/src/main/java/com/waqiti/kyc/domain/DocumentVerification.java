package com.waqiti.kyc.domain;

import com.waqiti.kyc.service.KYCDocumentVerificationService.DocumentType;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a document verification process
 */
@Entity
@Table(name = "document_verifications", indexes = {
    @Index(name = "idx_doc_verification_user", columnList = "user_id"),
    @Index(name = "idx_doc_verification_status", columnList = "status"),
    @Index(name = "idx_doc_verification_created", columnList = "created_at"),
    @Index(name = "idx_doc_verification_type", columnList = "document_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"extractedData", "metadata"})
@EqualsAndHashCode(exclude = {"extractedData", "metadata"})
public class DocumentVerification {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "document_key")
    private String documentKey;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "authenticity_score")
    private Double authenticityScore;

    @Column(name = "data_match_score")
    private Double dataMatchScore;

    @Column(name = "fraud_score")
    private Double fraudScore;

    @Column(name = "final_score")
    private Double finalScore;

    @Type(type = "jsonb")
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private Map<String, String> extractedData;

    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == Status.VERIFIED || status == Status.REJECTED || status == Status.FAILED) {
            if (completedAt == null) {
                completedAt = LocalDateTime.now();
            }
        }
    }

    public enum Status {
        PENDING,           // Initial state
        PROCESSING,        // Being processed
        PENDING_REVIEW,    // Requires manual review
        IN_REVIEW,         // Currently being reviewed
        VERIFIED,          // Successfully verified
        REJECTED,          // Rejected
        FAILED,           // Processing failed
        EXPIRED           // Verification expired
    }

    /**
     * Check if verification is complete
     */
    public boolean isComplete() {
        return status == Status.VERIFIED || 
               status == Status.REJECTED || 
               status == Status.FAILED ||
               status == Status.EXPIRED;
    }

    /**
     * Check if verification was successful
     */
    public boolean isSuccessful() {
        return status == Status.VERIFIED;
    }

    /**
     * Check if verification requires manual review
     */
    public boolean requiresReview() {
        return status == Status.PENDING_REVIEW || status == Status.IN_REVIEW;
    }

    /**
     * Check if verification has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}