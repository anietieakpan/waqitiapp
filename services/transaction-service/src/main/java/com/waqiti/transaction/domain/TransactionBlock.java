package com.waqiti.transaction.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "transaction_blocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionBlock {
    
    @Id
    private String id;
    
    @Column(nullable = false)
    private String eventId;
    
    @Column(nullable = false)
    private String transactionId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockReason blockReason;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockStatus status;
    
    @Column
    private String severity;
    
    @Column(length = 1000)
    private String description;
    
    @Column
    private String blockedBy;
    
    @Column
    private String blockedBySystem;
    
    @Column(nullable = false)
    private LocalDateTime blockTimestamp;
    
    @Column
    private LocalDateTime expirationTime;
    
    @Column
    private String originalTransactionStatus;
    
    @ElementCollection
    @CollectionTable(name = "transaction_block_metadata")
    private Map<String, String> blockMetadata;
    
    @Column
    private String correlationId;
    
    @Column
    private Boolean requiresManualReview = false;
    
    @ElementCollection
    @CollectionTable(name = "transaction_block_compliance_flags")
    private Map<String, String> complianceFlags;
    
    @ElementCollection
    @CollectionTable(name = "transaction_block_fraud_indicators")
    private Map<String, String> fraudIndicators;
    
    @Column
    private Boolean recoveryEligible = true;
    
    @Column
    private Boolean notificationsSent = false;
    
    @Column
    private String actionTaken;
    
    @Column
    private LocalDateTime actionTimestamp;
    
    @Column(length = 1000)
    private String errorMessage;
    
    @Column(precision = 5, scale = 4)
    private BigDecimal complianceRiskScore;
    
    @Column(precision = 5, scale = 4)
    private BigDecimal fraudRiskScore;
    
    @Column
    private Boolean regulatoryReporting = false;
    
    @Column
    private LocalDateTime reviewedAt;
    
    @Column
    private String reviewedBy;
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
}