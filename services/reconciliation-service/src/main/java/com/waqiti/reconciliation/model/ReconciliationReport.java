package com.waqiti.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing reconciliation reports and summaries
 */
@Entity
@Table(name = "reconciliation_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "reconciliation_type", length = 50)
    private String reconciliationType;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "total_transactions_processed", nullable = false)
    private Integer totalTransactionsProcessed;

    @Column(name = "matched_transactions", nullable = false)
    private Integer matchedTransactions;

    @Column(name = "discrepancy_count", nullable = false)
    private Integer discrepancyCount;

    @Column(name = "pending_count", nullable = false)
    private Integer pendingCount;

    @Column(name = "total_discrepancy_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalDiscrepancyAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "high_value_transactions")
    private Integer highValueTransactions;

    @Column(name = "orphaned_transactions")
    private Integer orphanedTransactions;

    @Column(name = "data_quality_score", precision = 5, scale = 2)
    private BigDecimal dataQualityScore;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "report_format", length = 20)
    @Builder.Default
    private String reportFormat = "JSON";

    public enum ReportStatus {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_ERRORS,
        FAILED,
        CANCELLED,
        TIMEOUT
    }

    // Utility methods
    public boolean hasErrors() {
        return status == ReportStatus.COMPLETED_WITH_ERRORS || status == ReportStatus.FAILED;
    }

    public boolean isSuccessful() {
        return status == ReportStatus.COMPLETED;
    }

    public double getMatchingPercentage() {
        if (totalTransactionsProcessed == 0) {
            return 0.0;
        }
        return (matchedTransactions * 100.0) / totalTransactionsProcessed;
    }

    public double getDiscrepancyPercentage() {
        if (totalTransactionsProcessed == 0) {
            return 0.0;
        }
        return (discrepancyCount * 100.0) / totalTransactionsProcessed;
    }

    public long getExecutionDurationSeconds() {
        if (executionDurationMs != null) {
            return executionDurationMs / 1000;
        }
        return java.time.Duration.between(startTime, endTime).getSeconds();
    }

    public boolean requiresAttention() {
        return hasErrors() || discrepancyCount > 0 || 
               totalDiscrepancyAmount.compareTo(new BigDecimal("1000")) > 0;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (executionDurationMs == null && startTime != null && endTime != null) {
            executionDurationMs = java.time.Duration.between(startTime, endTime).toMillis();
        }
    }
}