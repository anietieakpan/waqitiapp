package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compensation Record Domain Entity
 * 
 * Represents a compensation record for failed wallet operations
 * that require remediation or manual intervention
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "compensation_records")
public class CompensationRecord {
    
    @Id
    private UUID id;
    
    @Indexed
    private String paymentId;
    
    @Indexed
    private String userId;
    
    @Indexed
    private String walletId;
    
    private BigDecimal amount;
    private String currency;
    
    private String failureReason;
    private String failureCode;
    
    private String errorMessage;
    private String errorStackTrace;
    
    @Indexed
    private CompensationStatus status;
    
    private String compensationType;
    
    private Integer attemptCount;
    private Integer maxAttempts;
    
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime completedAt;
    
    private String lastErrorMessage;
    
    private Map<String, Object> metadata;
    
    @Indexed
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
}