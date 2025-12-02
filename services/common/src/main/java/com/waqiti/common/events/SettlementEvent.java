package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * CRITICAL EVENT: Published when payment provider completes daily settlement
 * 
 * This event triggers reconciliation of settled transactions against expected amounts.
 * Settlement discrepancies can indicate missing transactions, double processing, or fraud.
 * 
 * PRODUCTION-READY: Comprehensive settlement data for financial reconciliation
 * 
 * Published by: payment-service (after provider settlement completion)
 * Consumed by: reconciliation-service (for settlement reconciliation)
 * 
 * Business Impact: CRITICAL - Settlement mismatches directly impact cash flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementEvent implements DomainEvent, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique event identifier
     */
    private String eventId;
    
    /**
     * Event timestamp
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * Event version for schema evolution
     */
    @Builder.Default
    private String eventVersion = "1.0";
    
    /**
     * External settlement ID from payment provider
     */
    private String settlementId;
    
    /**
     * Payment provider name (Stripe, Adyen, PayPal, etc.)
     */
    private String paymentProvider;
    
    /**
     * Settlement batch identifier
     */
    private String batchId;
    
    /**
     * Total settlement amount from provider
     */
    private BigDecimal settlementAmount;
    
    /**
     * Settlement currency
     */
    private String currency;
    
    /**
     * Date of settlement
     */
    private LocalDate settlementDate;
    
    /**
     * Expected settlement date (for reconciliation timing)
     */
    private LocalDate expectedSettlementDate;
    
    /**
     * Number of transactions included in settlement
     */
    private Integer transactionCount;
    
    /**
     * Settlement type: STANDARD, EXPRESS, INSTANT
     */
    private String settlementType;
    
    /**
     * Settlement status: PENDING, COMPLETED, FAILED, PARTIAL
     */
    private String settlementStatus;
    
    /**
     * Total fees deducted by provider
     */
    private BigDecimal providerFees;
    
    /**
     * Total refunds included in settlement
     */
    private BigDecimal refundAmount;
    
    /**
     * Total chargebacks deducted
     */
    private BigDecimal chargebackAmount;
    
    /**
     * Net settlement amount (settlement - fees - refunds - chargebacks)
     */
    private BigDecimal netSettlementAmount;
    
    /**
     * Beneficiary bank account for settlement funds
     */
    private String beneficiaryAccount;
    
    /**
     * Beneficiary bank name
     */
    private String beneficiaryBank;
    
    /**
     * Settlement reference number
     */
    private String settlementReference;
    
    /**
     * Provider settlement report URL
     */
    private String settlementReportUrl;
    
    /**
     * Additional settlement metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Correlation ID for distributed tracing
     */
    private String correlationId;
    
    /**
     * User ID who triggered the settlement
     */
    private String userId;
    
    /**
     * Event version number
     */
    @Builder.Default
    private Long version = 1L;
    
    /**
     * Settlement period start date
     */
    private LocalDate periodStartDate;
    
    /**
     * Settlement period end date
     */
    private LocalDate periodEndDate;
    
    /**
     * Indicates if this is an automatic or manual settlement
     */
    @Builder.Default
    private Boolean automaticSettlement = true;
    
    /**
     * Settlement reconciliation deadline
     */
    private LocalDateTime reconciliationDeadline;
    
    /**
     * Priority level for reconciliation: CRITICAL, HIGH, NORMAL
     */
    @Builder.Default
    private String reconciliationPriority = "CRITICAL";
    
    /**
     * Indicates if settlement requires immediate investigation
     */
    @Builder.Default
    private Boolean requiresInvestigation = false;
    
    /**
     * Notes or comments about the settlement
     */
    private String notes;
    
    @Override
    public String getEventType() {
        return "SETTLEMENT_COMPLETED";
    }
    
    @Override
    public String getAggregateId() {
        return settlementId;
    }
    
    @Override
    public String getAggregateName() {
        return "Settlement";
    }
    
    @Override
    public String getAggregateType() {
        return "Settlement";
    }
    
    @Override
    public Instant getTimestamp() {
        return eventTimestamp != null ? eventTimestamp.toInstant(ZoneOffset.UTC) : Instant.now();
    }
    
    @Override
    public String getTopic() {
        return "settlement-events";
    }
    
    @Override
    public String getSourceService() {
        return "payment-service";
    }
    
    /**
     * Calculate total deductions (fees + refunds + chargebacks)
     */
    public BigDecimal getTotalDeductions() {
        BigDecimal total = BigDecimal.ZERO;
        
        if (providerFees != null) {
            total = total.add(providerFees);
        }
        
        if (refundAmount != null) {
            total = total.add(refundAmount);
        }
        
        if (chargebackAmount != null) {
            total = total.add(chargebackAmount);
        }
        
        return total;
    }
    
    /**
     * Verify settlement amounts are consistent
     */
    public boolean isAmountConsistent() {
        if (settlementAmount == null || netSettlementAmount == null) {
            return false;
        }
        
        BigDecimal calculatedNet = settlementAmount.subtract(getTotalDeductions());
        return calculatedNet.compareTo(netSettlementAmount) == 0;
    }
    
    /**
     * Check if settlement is overdue for reconciliation
     */
    public boolean isOverdueReconciliation() {
        if (reconciliationDeadline == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(reconciliationDeadline);
    }
    
    /**
     * Get settlement age in days
     */
    public long getSettlementAgeDays() {
        if (settlementDate == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(settlementDate, LocalDate.now());
    }
}