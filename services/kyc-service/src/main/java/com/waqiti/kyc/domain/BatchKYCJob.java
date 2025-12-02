package com.waqiti.kyc.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Entity representing a batch KYC verification job
 */
@Entity
@Table(name = "batch_kyc_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchKYCJob extends BaseEntity {
    
    @Column(nullable = false)
    private String organizationId;
    
    @Column(nullable = false)
    private String requestedBy;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.PENDING;
    
    @Column(nullable = false)
    private Integer totalUsers = 0;
    
    @Column(nullable = false)
    private Integer processedUsers = 0;
    
    @Column(nullable = false)
    private Integer successfulUsers = 0;
    
    @Column(nullable = false)
    private Integer failedUsers = 0;
    
    private String provider;
    
    private String verificationLevel;
    
    private LocalDateTime startedAt;
    
    private LocalDateTime completedAt;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    private String parentJobId;
    
    @Column(nullable = false)
    private Boolean notifyOnCompletion = false;
    
    @ElementCollection
    @CollectionTable(name = "batch_kyc_results", joinColumns = @JoinColumn(name = "batch_id"))
    @MapKeyColumn(name = "user_id")
    @Column(name = "verification_id")
    private Map<String, String> results = new HashMap<>();
    
    @ElementCollection
    @CollectionTable(name = "batch_kyc_errors", joinColumns = @JoinColumn(name = "batch_id"))
    @MapKeyColumn(name = "user_id")
    @Column(name = "error_message", columnDefinition = "TEXT")
    private Map<String, String> errors = new HashMap<>();
    
    @Column(columnDefinition = "json")
    private String metadata;
    
    public enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED,
        PARTIALLY_COMPLETED
    }
    
    public void incrementProcessed() {
        this.processedUsers++;
    }
    
    public void incrementSuccessful() {
        this.successfulUsers++;
    }
    
    public void incrementFailed() {
        this.failedUsers++;
    }
    
    public double getProgressPercentage() {
        if (totalUsers == 0) return 0.0;
        return (double) processedUsers / totalUsers * 100;
    }
    
    public double getSuccessRate() {
        if (processedUsers == 0) return 0.0;
        return (double) successfulUsers / processedUsers * 100;
    }
}