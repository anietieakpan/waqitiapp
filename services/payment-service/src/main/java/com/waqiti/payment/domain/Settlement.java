package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Settlement entity - MongoDB document
 * CRITICAL: Added @Version for optimistic locking to prevent settlement processing conflicts
 * Ensures settlement amounts and status changes are not lost due to concurrent updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "settlements")
public class Settlement {
    @Id
    private String id;

    /**
     * Optimistic locking version field for MongoDB
     * Critical for preventing concurrent settlement processing errors
     */
    @Version
    private Long version;
    private String eventId;
    private String referenceId;
    private String merchantId;
    private String merchantAccountId;
    private SettlementType settlementType;
    private String provider;
    private String currency;
    
    // Amounts
    private BigDecimal requestedAmount;
    private BigDecimal grossAmount;
    private BigDecimal processingFee;
    private BigDecimal platformFee;
    private BigDecimal providerFee;
    private BigDecimal refundAmount;
    private BigDecimal chargebackAmount;
    private BigDecimal adjustmentAmount;
    private String adjustmentReason;
    private BigDecimal totalFees;
    private BigDecimal netAmount;
    private BigDecimal finalAmount;
    private BigDecimal expressFee;
    
    // Settlement details
    private LocalDate settlementDate;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer paymentCount;
    private List<String> paymentIds;
    private String settlementMethod;
    private boolean splitSettlement;
    private Map<String, BigDecimal> splitDetails;
    private boolean scheduled;
    private boolean requiresApproval;
    private String approvalStatus;
    
    // Transfer details
    private String transferId;
    private String transferReference;
    private LocalDateTime transferInitiatedAt;
    private String transferStatus;
    private LocalDateTime transferredAt;
    private String transferError;
    
    // Reconciliation
    private boolean reconciled;
    private LocalDateTime reconciledAt;
    private BigDecimal reconciliationDiscrepancy;
    private String reconciliationNotes;
    private String reconciliationError;
    
    // Status
    private SettlementStatus status;
    private boolean eligible;
    private String ineligibilityReason;
    private LocalDateTime eligibilityVerifiedAt;
    private boolean onHold;
    private String holdReason;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String failureReason;
    
    // Metadata
    private String correlationId;
    private Long processingTimeMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

