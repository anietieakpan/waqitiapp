package com.waqiti.corebanking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Statement Generation Job Entity
 * Tracks the status and details of statement generation jobs
 */
@Entity
@Table(name = "statement_jobs", indexes = {
    @Index(name = "idx_statement_job_account_id", columnList = "account_id"),
    @Index(name = "idx_statement_job_status", columnList = "status"),
    @Index(name = "idx_statement_job_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "job_id")
    private UUID jobId;
    
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private StatementFormat format;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;
    
    @Column(name = "message")
    private String message;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "estimated_completion_time")
    private LocalDateTime estimatedCompletionTime;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "download_url")
    private String downloadUrl;
    
    @Column(name = "download_expires_at")
    private LocalDateTime downloadExpiresAt;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "max_retry_attempts", nullable = false)
    private Integer maxRetryAttempts = 3;
    
    @Column(name = "priority", nullable = false)
    private Integer priority = 1;
    
    @Column(name = "transaction_count")
    private Integer transactionCount;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = JobStatus.PENDING;
        }
        if (estimatedCompletionTime == null) {
            estimatedCompletionTime = LocalDateTime.now().plusMinutes(5);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        if (status == JobStatus.IN_PROGRESS && startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if ((status == JobStatus.COMPLETED || status == JobStatus.FAILED) && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }
    
    public enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED
    }
    
    public enum StatementFormat {
        PDF,
        CSV,
        EXCEL,
        JSON
    }
    
    /**
     * Check if job can be retried
     */
    public boolean canRetry() {
        return status == JobStatus.FAILED && retryCount < maxRetryAttempts;
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    /**
     * Check if download link has expired
     */
    public boolean isDownloadExpired() {
        return downloadExpiresAt != null && LocalDateTime.now().isAfter(downloadExpiresAt);
    }
    
    /**
     * Mark job as started
     */
    public void markAsStarted() {
        this.status = JobStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        this.message = "Statement generation in progress";
    }
    
    /**
     * Mark job as completed
     */
    public void markAsCompleted(String filePath, Long fileSize, String downloadUrl) {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.downloadUrl = downloadUrl;
        this.downloadExpiresAt = LocalDateTime.now().plusDays(7); // 7-day expiry
        this.message = "Statement generated successfully";
        
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }
    
    /**
     * Mark job as failed
     */
    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
        this.message = "Statement generation failed";
        
        if (startedAt != null) {
            this.processingTimeMs = java.time.Duration.between(startedAt, completedAt).toMillis();
        }
    }
    
    /**
     * Mark job as cancelled
     */
    public void markAsCancelled() {
        this.status = JobStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
        this.message = "Statement generation cancelled";
    }
}