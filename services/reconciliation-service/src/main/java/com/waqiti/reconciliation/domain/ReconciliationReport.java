package com.waqiti.reconciliation.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReconciliationReport - Summary report for reconciliation batch
 *
 * Contains aggregated statistics and results from reconciliation process.
 * Used for audit, compliance, and operational monitoring.
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Entity
@Table(name = "reconciliation_reports", indexes = {
    @Index(name = "idx_recon_report_batch", columnList = "reconciliation_batch_id"),
    @Index(name = "idx_recon_report_status", columnList = "status"),
    @Index(name = "idx_recon_report_type", columnList = "report_type"),
    @Index(name = "idx_recon_report_date", columnList = "report_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ReconciliationReport {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "reconciliation_batch_id", nullable = false, unique = true)
    private String reconciliationBatchId;

    @Column(name = "report_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    @Column(name = "report_date", nullable = false)
    private LocalDateTime reportDate;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "total_items_processed", nullable = false)
    private Long totalItemsProcessed;

    @Column(name = "total_matched", nullable = false)
    private Long totalMatched;

    @Column(name = "total_unmatched", nullable = false)
    private Long totalUnmatched;

    @Column(name = "total_discrepancies", nullable = false)
    private Long totalDiscrepancies;

    @Column(name = "total_amount_processed", precision = 19, scale = 4)
    private BigDecimal totalAmountProcessed;

    @Column(name = "total_amount_matched", precision = 19, scale = 4)
    private BigDecimal totalAmountMatched;

    @Column(name = "total_amount_discrepancy", precision = 19, scale = 4)
    private BigDecimal totalAmountDiscrepancy;

    @Column(name = "match_rate_percentage", precision = 5, scale = 2)
    private BigDecimal matchRatePercentage;

    @Column(name = "critical_discrepancies", nullable = false)
    private Long criticalDiscrepancies;

    @Column(name = "high_discrepancies", nullable = false)
    private Long highDiscrepancies;

    @Column(name = "medium_discrepancies", nullable = false)
    private Long mediumDiscrepancies;

    @Column(name = "low_discrepancies", nullable = false)
    private Long lowDiscrepancies;

    @Column(name = "processing_duration_seconds")
    private Long processingDurationSeconds;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "report_data", columnDefinition = "TEXT")
    private String reportData;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ReportStatus.DRAFT;
        }
        calculateMatchRate();
    }

    public enum ReportType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUAL,
        AD_HOC,
        REGULATORY
    }

    public enum ReportStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        PUBLISHED,
        ARCHIVED
    }

    private void calculateMatchRate() {
        if (totalItemsProcessed != null && totalItemsProcessed > 0) {
            BigDecimal matched = BigDecimal.valueOf(totalMatched != null ? totalMatched : 0);
            BigDecimal total = BigDecimal.valueOf(totalItemsProcessed);
            matchRatePercentage = matched.divide(total, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        } else {
            matchRatePercentage = BigDecimal.ZERO;
        }
    }

    public void markAsCompleted() {
        this.status = ReportStatus.PENDING_REVIEW;
        this.completedAt = LocalDateTime.now();
        if (startedAt != null) {
            processingDurationSeconds = java.time.Duration.between(startedAt, completedAt).getSeconds();
        }
        calculateMatchRate();
    }

    public void approve(String approvedBy) {
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.status = ReportStatus.APPROVED;
    }

    public void publish() {
        this.status = ReportStatus.PUBLISHED;
    }

    public boolean hasHighSeverityIssues() {
        return (criticalDiscrepancies != null && criticalDiscrepancies > 0) ||
               (highDiscrepancies != null && highDiscrepancies > 0);
    }
}
