package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Domain model for payment disputes.
 * Represents a customer or merchant dispute against a payment transaction.
 * 
 * Features:
 * - Complete dispute lifecycle management
 * - Fraud risk scoring integration
 * - Evidence document tracking
 * - SLA monitoring
 * - Regulatory compliance fields
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "payment_disputes")
@CompoundIndexes({
    @CompoundIndex(name = "payment_event_idx", def = "{'paymentId': 1, 'eventId': 1}", unique = true),
    @CompoundIndex(name = "status_priority_idx", def = "{'status': 1, 'priority': 1}"),
    @CompoundIndex(name = "merchant_date_idx", def = "{'merchantId': 1, 'initiatedAt': -1}"),
    @CompoundIndex(name = "customer_date_idx", def = "{'customerId': 1, 'initiatedAt': -1}")
})
public class PaymentDispute {

    @Id
    private String id;

    @NotNull
    @Indexed
    private String paymentId;

    @NotNull
    @Indexed
    private String eventId; // For idempotency

    @NotNull
    private DisputeReason disputeReason;

    @NotNull
    @Positive
    private BigDecimal disputeAmount;

    @NotNull
    private DisputeStatus status;

    private String customerDescription;
    private String merchantDescription;
    private List<String> evidenceDocuments;

    @NotNull
    @Indexed
    private String initiatedBy; // User ID who initiated the dispute

    @NotNull
    private LocalDateTime initiatedAt;

    private LocalDateTime dueDate;
    private LocalDateTime resolvedAt;

    @NotNull
    private String priority; // HIGH, MEDIUM, LOW

    // Original transaction details
    private BigDecimal originalTransactionAmount;
    private String originalTransactionCurrency;
    private LocalDateTime originalTransactionDate;

    @Indexed
    private String merchantId;

    @Indexed
    private String customerId;

    // External system references
    private String externalDisputeId; // From payment processor
    private String chargebackId; // If escalated to chargeback

    // Fraud detection
    private Double fraudRiskScore;
    private List<String> fraudIndicators;

    // Processing metadata
    private String processingNotes;
    private Boolean requiresManualReview;
    private String assignedToUserId;
    private String resolutionReason;
    private String resolutionNotes;

    // Compliance and audit
    private Map<String, Object> complianceMetadata;
    private List<String> regulatoryFlags;

    // Timestamps
    @NotNull
    private LocalDateTime createdAt;

    @NotNull
    private LocalDateTime updatedAt;

    // Business methods
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) && 
               !status.isTerminal();
    }

    public boolean canBeEscalated() {
        return status == DisputeStatus.INITIATED || status == DisputeStatus.UNDER_REVIEW;
    }

    public boolean isHighValue() {
        return disputeAmount.compareTo(new BigDecimal("5000")) > 0;
    }

    public boolean requiresUrgentAttention() {
        return isHighValue() || 
               (fraudRiskScore != null && fraudRiskScore > 0.8) ||
               priority.equals("HIGH");
    }

    public void markAsResolved(String resolutionReason, String notes) {
        this.status = DisputeStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionReason = resolutionReason;
        this.resolutionNotes = notes;
        this.updatedAt = LocalDateTime.now();
    }

    public void escalateToChargeback(String chargebackId) {
        this.status = DisputeStatus.ESCALATED_TO_CHARGEBACK;
        this.chargebackId = chargebackId;
        this.priority = "HIGH";
        this.updatedAt = LocalDateTime.now();
    }
}