package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Suspicious Activity Report (SAR) entity for regulatory compliance
 */
@Entity
@Table(name = "suspicious_activity_reports", indexes = {
    @Index(name = "idx_sar_user_id", columnList = "user_id"),
    @Index(name = "idx_sar_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_sar_report_number", columnList = "report_number", unique = true),
    @Index(name = "idx_sar_status", columnList = "status"),
    @Index(name = "idx_sar_filed_date", columnList = "filed_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SuspiciousActivityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_number", nullable = false, unique = true)
    private String reportNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "suspicious_activity", columnDefinition = "TEXT", nullable = false)
    private String suspiciousActivity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SarStatus status;

    @Column(name = "filed_date", nullable = false)
    private LocalDateTime filedDate;

    @Column(name = "reporting_institution", nullable = false)
    private String reportingInstitution;

    @Column(name = "jurisdiction_code", length = 2, nullable = false)
    private String jurisdictionCode;

    @Column(name = "requires_immediate_attention", nullable = false)
    private boolean requiresImmediateAttention;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submission_reference")
    private String submissionReference;

    @Column(name = "regulatory_authority")
    private String regulatoryAuthority; // e.g., "FinCEN", "FCA", etc.

    @Column(name = "priority_level")
    private Integer priorityLevel; // 1-5 scale

    @Column(name = "investigation_summary", columnDefinition = "TEXT")
    private String investigationSummary;

    @Column(name = "supporting_documentation", columnDefinition = "TEXT")
    private String supportingDocumentation;

    @Column(name = "follow_up_required")
    private Boolean followUpRequired;

    @Column(name = "follow_up_date")
    private LocalDateTime followUpDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SarStatus {
        PENDING_REVIEW,
        UNDER_REVIEW,
        APPROVED_FOR_SUBMISSION,
        SUBMITTED,
        ACKNOWLEDGED,
        FOLLOW_UP_REQUIRED,
        CLOSED,
        REJECTED
    }
}