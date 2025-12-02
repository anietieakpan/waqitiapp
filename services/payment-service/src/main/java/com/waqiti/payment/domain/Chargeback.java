package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Chargeback entity
 * CRITICAL: Added @Version for optimistic locking to prevent chargeback processing conflicts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chargebacks")
public class Chargeback {
    @Id
    private String id;

    @Version
    private Long version;

    private String eventId;
    private String paymentId;
    private String merchantId;
    private String userId;
    
    // Chargeback details
    private String providerChargebackId;
    private String provider;
    private String reasonCode;
    private ChargebackReason reason;
    private BigDecimal chargebackAmount;
    private BigDecimal originalAmount;
    private String currency;
    
    // Stage and status
    private ChargebackStage stage;
    private ChargebackStatus status;
    private LocalDateTime receivedAt;
    private LocalDateTime dueDate;
    
    // Eligibility
    private boolean eligible;
    private String ineligibilityReason;
    private LocalDateTime eligibilityVerifiedAt;
    
    // Liability
    private boolean liabilityShift;
    private String liabilityHolder;
    private String liabilityReason;
    private LocalDateTime liabilityAssessedAt;
    
    // Evidence
    private List<Map<String, Object>> evidenceCollected;
    private LocalDateTime evidenceCollectedAt;
    private Double evidenceScore;
    private String evidenceCollectionError;
    
    // Response strategy
    private String responseStrategy;
    private boolean defensible;
    private LocalDateTime strategyDeterminedAt;
    
    // Response submission
    private boolean responseSubmitted;
    private LocalDateTime responseSubmittedAt;
    private String providerResponseId;
    private String providerResponseStatus;
    private String responseError;
    
    // Merchant handling
    private boolean merchantDebited;
    private BigDecimal merchantDebitAmount;
    private LocalDateTime merchantDebitedAt;
    private String merchantDebitTransactionId;
    private String merchantDebitError;
    
    // Financial details
    private boolean fundsHeld;
    private BigDecimal fundsHeldAmount;
    private LocalDateTime fundsHeldAt;
    private BigDecimal chargebackFee;
    
    // Dispute management
    private String disputeCaseId;
    private LocalDateTime disputeCaseCreatedAt;
    
    // Actions tracking
    private Integer successfulActions;
    private List<String> failedActions;
    
    // Acceptance
    private LocalDateTime acceptedAt;
    private String acceptanceReason;
    
    // Metadata
    private String correlationId;
    private Long processingTimeMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

