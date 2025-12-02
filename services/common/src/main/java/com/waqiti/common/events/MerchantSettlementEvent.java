package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event published when merchant settlement is calculated and ready for payout
 * 
 * This event triggers:
 * - Merchant payout initiation in payment service
 * - Settlement reconciliation between accounting and payment
 * - Merchant balance updates
 * - Merchant notification of settlement
 * - T+2 payout scheduling
 * 
 * SETTLEMENT CALCULATION:
 * Net Settlement = Gross Sales - Platform Fees - Refunds - Chargebacks + Adjustments
 * 
 * @author Waqiti Merchant Platform
 * @version 1.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettlementEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique settlement identifier
     */
    @JsonProperty("settlement_id")
    private String settlementId;
    
    /**
     * Merchant ID receiving settlement
     */
    @JsonProperty("merchant_id")
    private String merchantId;
    
    /**
     * Gross payment amount before deductions
     */
    @JsonProperty("gross_amount")
    private BigDecimal grossAmount;
    
    /**
     * Total platform fees (processing + service fees)
     */
    @JsonProperty("total_fees")
    private BigDecimal totalFees;
    
    /**
     * Total refunds processed in settlement period
     */
    @JsonProperty("total_refunds")
    private BigDecimal totalRefunds;
    
    /**
     * Total chargebacks in settlement period
     */
    @JsonProperty("total_chargebacks")
    private BigDecimal totalChargebacks;
    
    /**
     * Net settlement amount = Gross - Fees - Refunds - Chargebacks + Adjustments
     */
    @JsonProperty("net_settlement_amount")
    private BigDecimal netSettlementAmount;
    
    /**
     * Currency code (ISO 4217)
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Settlement period start date
     */
    @JsonProperty("settlement_period_start")
    private LocalDateTime settlementPeriodStart;
    
    /**
     * Settlement period end date
     */
    @JsonProperty("settlement_period_end")
    private LocalDateTime settlementPeriodEnd;
    
    /**
     * Total transaction count in settlement period
     */
    @JsonProperty("transaction_count")
    private Integer transactionCount;
    
    /**
     * Merchant bank account ID for payout
     */
    @JsonProperty("merchant_bank_account_id")
    private String merchantBankAccountId;
    
    /**
     * Adjustments (credits/debits) applied to settlement
     */
    @JsonProperty("adjustments")
    private BigDecimal adjustments;
    
    /**
     * Settlement timestamp
     */
    @JsonProperty("settled_at")
    private LocalDateTime settledAt;
    
    /**
     * Additional metadata (fee breakdown, transaction IDs, etc.)
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;
}