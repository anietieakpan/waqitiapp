package com.waqiti.security.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Regulatory Report entity for compliance reporting
 */
@Entity
@Table(name = "regulatory_reports", indexes = {
    @Index(name = "idx_regulatory_reports_type", columnList = "report_type"),
    @Index(name = "idx_regulatory_reports_status", columnList = "status"),
    @Index(name = "idx_regulatory_reports_generated_at", columnList = "generated_at"),
    @Index(name = "idx_regulatory_reports_period", columnList = "reporting_period"),
    @Index(name = "idx_regulatory_reports_user_id", columnList = "user_id"),
    @Index(name = "idx_regulatory_reports_alert_id", columnList = "alert_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RegulatoryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "report_content", columnDefinition = "TEXT", nullable = false)
    private String reportContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReportStatus status;

    @CreatedDate
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "reporting_period", nullable = false)
    private LocalDateTime reportingPeriod;

    @Column(name = "jurisdiction_code", length = 2, nullable = false)
    private String jurisdictionCode;

    @Column(name = "compliance_officer_id")
    private UUID complianceOfficerId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "submission_reference")
    private String submissionReference;

    @Column(name = "regulatory_authority")
    private String regulatoryAuthority;

    @Column(name = "amount_reported", precision = 19, scale = 4)
    private BigDecimal amountReported;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "report_format")
    private String reportFormat; // XML, JSON, PDF, etc.

    @Column(name = "validation_status")
    private String validationStatus;

    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    @Column(name = "acknowledgment_received")
    private Boolean acknowledgmentReceived;

    @Column(name = "acknowledgment_date")
    private LocalDateTime acknowledgmentDate;

    @Column(name = "acknowledgment_reference")
    private String acknowledgmentReference;

    @Column(name = "follow_up_required")
    private Boolean followUpRequired;

    @Column(name = "follow_up_date")
    private LocalDateTime followUpDate;

    @Column(name = "retention_period_years")
    private Integer retentionPeriodYears;

    @Column(name = "confidentiality_level")
    private String confidentialityLevel;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ReportType {
        SAR,                    // Suspicious Activity Report
        CTR,                    // Currency Transaction Report
        AML_ALERT,              // AML Alert Report
        MONTHLY_COMPLIANCE,     // Monthly compliance summary
        QUARTERLY_COMPLIANCE,   // Quarterly compliance report
        ANNUAL_COMPLIANCE,      // Annual compliance report
        REGULATORY_EXAMINATION, // Response to regulatory examination
        SELF_ASSESSMENT,        // Self-assessment report
        INCIDENT_REPORT,        // Security incident report
        SANCTIONS_SCREENING,    // Sanctions screening report
        KYC_SUMMARY,           // KYC compliance summary
        TRANSACTION_MONITORING  // Transaction monitoring report
    }

    public enum ReportStatus {
        DRAFT,
        GENERATED,
        VALIDATED,
        SUBMITTED,
        ACKNOWLEDGED,
        REJECTED,
        RESUBMITTED,
        ARCHIVED,
        ERROR
    }
}