package com.waqiti.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a permanent failure audit record
 *
 * <p><b>⚠️ CRITICAL COMPLIANCE REQUIREMENT:</b></p>
 * <p>Records in this table CANNOT be deleted and MUST be retained for 7 years
 * per SOX/GDPR compliance requirements. A database trigger prevents deletion.</p>
 *
 * <p>This table serves as the official audit trail for all non-recoverable
 * message processing failures, enabling compliance reporting and forensic analysis.</p>
 *
 * <h3>Retention Policy:</h3>
 * <ul>
 *   <li>Minimum Retention: 7 years from recorded_at</li>
 *   <li>Auto-calculated field: audit_retention_until</li>
 *   <li>Deletion Prevention: Database trigger blocks DELETE operations</li>
 *   <li>PII Protection: All sensitive data MUST be masked in payload</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Entity
@Table(name = "dlq_permanent_failures", indexes = {
    @Index(name = "idx_permanent_failures_topic", columnList = "original_topic, failed_at"),
    @Index(name = "idx_permanent_failures_recorded", columnList = "recorded_at"),
    @Index(name = "idx_permanent_failures_retention", columnList = "audit_retention_until"),
    @Index(name = "idx_permanent_failures_category", columnList = "failure_category, failed_at"),
    @Index(name = "idx_permanent_failures_impact", columnList = "business_impact"),
    @Index(name = "idx_permanent_failures_compliance", columnList = "compliance_reviewed"),
    @Index(name = "idx_permanent_failures_remediation", columnList = "remediation_required, remediation_status"),
    @Index(name = "idx_permanent_failures_correlation", columnList = "correlation_id"),
    @Index(name = "idx_permanent_failures_handler", columnList = "handler_name, failed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"payload", "exceptionStackTrace", "contextData"})
public class PermanentFailureRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Original Message Metadata
    @Column(name = "original_topic", nullable = false, length = 255)
    private String originalTopic;

    @Column(name = "original_partition", nullable = false)
    private Integer originalPartition;

    @Column(name = "original_offset", nullable = false)
    private Long originalOffset;

    @Column(name = "original_key", length = 500)
    private String originalKey;

    // Message Payload (sanitized - ALL PII MUST BE MASKED)
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    // Failure Classification
    @Column(name = "failure_reason", nullable = false, length = 500)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_category", nullable = false, length = 100)
    private FailureCategory failureCategory;

    // Error Information
    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Column(name = "exception_class", length = 500)
    private String exceptionClass;

    @Column(name = "exception_stack_trace", columnDefinition = "TEXT")
    private String exceptionStackTrace;

    // Failure Metadata
    @Column(name = "failed_at", nullable = false)
    private LocalDateTime failedAt;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "retry_attempts", nullable = false)
    @Builder.Default
    private Integer retryAttempts = 0;

    // Handler Information
    @Column(name = "handler_name", nullable = false, length = 255)
    private String handlerName;

    @Column(name = "recovery_attempts", columnDefinition = "TEXT")
    private String recoveryAttempts;  // JSON array of recovery attempts

    // Compliance & Audit (CRITICAL)
    @Column(name = "audit_retention_until", nullable = false)
    private LocalDate auditRetentionUntil;

    @Column(name = "compliance_reviewed")
    @Builder.Default
    private Boolean complianceReviewed = false;

    @Column(name = "compliance_reviewed_at")
    private LocalDateTime complianceReviewedAt;

    @Column(name = "compliance_reviewed_by", length = 255)
    private String complianceReviewedBy;

    // Business Impact Assessment
    @Enumerated(EnumType.STRING)
    @Column(name = "business_impact", length = 50)
    private BusinessImpact businessImpact;

    @Column(name = "impact_description", columnDefinition = "TEXT")
    private String impactDescription;

    @Column(name = "financial_impact_amount", precision = 19, scale = 4)
    private BigDecimal financialImpactAmount;

    @Column(name = "financial_impact_currency", length = 3)
    @Builder.Default
    private String financialImpactCurrency = "USD";

    // Correlation for Tracing
    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "trace_id", length = 255)
    private String traceId;

    // Additional Context
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private Map<String, Object> contextData;

    // Audit Fields
    @Column(name = "created_by", nullable = false, length = 255)
    @Builder.Default
    private String createdBy = "system";

    // Remediation Tracking
    @Column(name = "remediation_required")
    @Builder.Default
    private Boolean remediationRequired = false;

    @Column(name = "remediation_status", length = 50)
    private String remediationStatus;

    @Column(name = "remediation_notes", columnDefinition = "TEXT")
    private String remediationNotes;

    @Column(name = "remediation_completed_at")
    private LocalDateTime remediationCompletedAt;

    /**
     * Failure category enumeration for classification
     */
    public enum FailureCategory {
        BUSINESS_RULE_VIOLATION,   // Failed business rule validation
        DATA_VALIDATION_ERROR,      // Invalid data format/content
        INVALID_STATE,              // Invalid entity state
        RESOURCE_NOT_FOUND,         // Required resource missing
        DUPLICATE_OPERATION,        // Duplicate/idempotency violation
        COMPLIANCE_BLOCK,           // Compliance/regulatory block
        MAX_RETRIES_EXCEEDED,       // Exhausted retry attempts
        UNRECOVERABLE_ERROR,        // System error, non-recoverable
        OTHER                       // Other/unclassified
    }

    /**
     * Business impact enumeration
     */
    public enum BusinessImpact {
        NONE,      // No business impact
        LOW,       // Minor impact, no customer effect
        MEDIUM,    // Moderate impact, limited customer effect
        HIGH,      // Significant impact, customer affected
        CRITICAL   // Critical impact, financial/regulatory consequences
    }

    /**
     * Pre-persist callback - auto-calculate retention date
     */
    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (retryAttempts == null) {
            retryAttempts = 0;
        }
        if (complianceReviewed == null) {
            complianceReviewed = false;
        }
        if (remediationRequired == null) {
            remediationRequired = false;
        }
        if (financialImpactCurrency == null) {
            financialImpactCurrency = "USD";
        }

        // CRITICAL: Auto-calculate 7-year retention date
        if (auditRetentionUntil == null) {
            auditRetentionUntil = recordedAt.toLocalDate().plusYears(7);
        }
    }

    /**
     * Mark as compliance reviewed
     */
    public void markComplianceReviewed(String reviewedBy) {
        this.complianceReviewed = true;
        this.complianceReviewedBy = reviewedBy;
        this.complianceReviewedAt = LocalDateTime.now();
    }

    /**
     * Set business impact assessment
     */
    public void assessBusinessImpact(BusinessImpact impact, String description, BigDecimal financialImpact) {
        this.businessImpact = impact;
        this.impactDescription = description;
        this.financialImpactAmount = financialImpact;
    }

    /**
     * Mark remediation required
     */
    public void requireRemediation(String initialStatus, String notes) {
        this.remediationRequired = true;
        this.remediationStatus = initialStatus;
        this.remediationNotes = notes;
    }

    /**
     * Complete remediation
     */
    public void completeRemediation(String notes) {
        this.remediationStatus = "COMPLETED";
        this.remediationNotes = (this.remediationNotes != null ? this.remediationNotes + "\n" : "") + notes;
        this.remediationCompletedAt = LocalDateTime.now();
    }

    /**
     * Check if record is eligible for archival (past retention date)
     */
    public boolean isEligibleForArchival() {
        return auditRetentionUntil != null &&
               LocalDate.now().isAfter(auditRetentionUntil);
    }

    /**
     * Get days remaining until retention expiration
     */
    public long getDaysUntilRetentionExpiration() {
        if (auditRetentionUntil == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), auditRetentionUntil);
    }
}
